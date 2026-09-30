"""Feature engineering shared by the three notebooks.

The feature list below is the contract with scoring-service. Its Java twin is
`scoring-service/.../model/ModelFeatures.java`, and a test there fails if the two disagree on
names or order. Every feature has to be computable from what scoring-service actually has:
the transaction event and the customer's rows in the `scored_transaction` ledger.
Counterparties are matched by name in Sentinel and by account here; both are the identity
of the other side of the payment.

The IBM AML data is a list of payments between accounts. Sentinel scores transactions from the
point of view of one customer, so each payment becomes two rows: a DEBIT for the account that
paid and a CREDIT for the account that received, both carrying the payment's label.
"""

from pathlib import Path

import numpy as np
import pandas as pd

DATA = Path(__file__).parent / "data" / "HI-Small_Trans.csv"
MODELS = Path(__file__).parent / "models"

FEATURES = [
    "log_amount_eur",       # ln(1 + amount in EUR)
    "is_credit",            # 1 = money in, 0 = money out
    "channel_cash",
    "channel_card",
    "channel_transfer",
    "hour_of_day",          # 0-23, UTC
    "log_count_24h",        # ln(1 + the customer's transactions in (t - 24h, t]), this one included
    "log_sum_24h",          # ln(1 + EUR total of those)
    "log_credit_count_24h", # ln(1 + money-in transactions among them)
    "log_cash_count_24h",   # ln(1 + cash transactions among them)
    "log_count_7d",         # ln(1 + transactions in (t - 7d, t]), this one included
    "log_amount_vs_7d",     # ln(1 + amount) - ln(1 + mean of the *earlier* 7-day amounts); 0 if none
    "is_new_counterparty",  # 1 when the customer has never dealt with this counterparty before
    "log_new_counterparties_24h",  # ln(1 + counterparties first seen in (t - 24h, t]): fan-out / fan-in
]

# Sentinel has three channels. Reinvestment (an account paying itself) and Bitcoin have no
# Sentinel equivalent and are dropped rather than forced into one.
CHANNELS = {
    "Cash": "CASH",
    "Credit Card": "CARD",
    "ACH": "TRANSFER",
    "Wire": "TRANSFER",
    "Cheque": "TRANSFER",
}

# Fixed, approximate EUR rates. The model needs amounts on one scale, not exact conversion.
EUR_PER_UNIT = {
    "Euro": 1.0, "US Dollar": 0.92, "UK Pound": 1.17, "Swiss Franc": 1.04, "Yen": 0.0062,
    "Yuan": 0.13, "Rupee": 0.011, "Canadian Dollar": 0.68, "Australian Dollar": 0.61,
    "Mexican Peso": 0.054, "Brazil Real": 0.18, "Ruble": 0.010, "Shekel": 0.25,
    "Saudi Riyal": 0.25,
}

# Counts go in as ln(1 + n): a busy account makes tens of thousands of payments a week, and on
# a linear scale those few accounts would dominate every distance KMeans measures.

DAY = 86_400
WEEK = 7 * DAY


def load_rows(path: Path = DATA) -> pd.DataFrame:
    """One row per (customer, transaction), sorted by customer then time, with features.

    Building them takes a minute or two, so the result is cached next to the CSV and rebuilt
    only when this file or the CSV is newer than the cache.
    """
    cache = path.with_suffix(".features.parquet")
    newest_input = max(path.stat().st_mtime, Path(__file__).stat().st_mtime)
    if cache.exists() and cache.stat().st_mtime > newest_input:
        return pd.read_parquet(cache)
    rows = build_rows(path)
    rows.to_parquet(cache)
    return rows


def build_rows(path: Path) -> pd.DataFrame:
    raw = pd.read_csv(path, dtype={"Account": str, "Account.1": str})
    raw = raw[raw["Payment Format"].isin(CHANNELS)]
    raw = raw[raw["Payment Currency"].isin(EUR_PER_UNIT)]
    raw = raw[(raw["From Bank"] != raw["To Bank"]) | (raw["Account"] != raw["Account.1"])]

    ts = pd.to_datetime(raw["Timestamp"], format="%Y/%m/%d %H:%M")
    amount = raw["Amount Paid"] * raw["Payment Currency"].map(EUR_PER_UNIT)
    channel = raw["Payment Format"].map(CHANNELS)

    payer = (raw["From Bank"].astype(str) + ":" + raw["Account"]).values
    payee = (raw["To Bank"].astype(str) + ":" + raw["Account.1"]).values
    # Sentinel records no counterparty for cash, so the training data must not have one either.
    cash = (channel == "CASH").values

    common = dict(ts=ts.values, amount_eur=amount.values, channel=channel.values,
                  label=raw["Is Laundering"].values)
    debits = pd.DataFrame(dict(customer=payer, counterparty=np.where(cash, None, payee),
                               is_credit=0, **common))
    credits = pd.DataFrame(dict(customer=payee, counterparty=np.where(cash, None, payer),
                                is_credit=1, **common))

    rows = pd.concat([debits, credits], ignore_index=True)
    rows = rows.sort_values(["customer", "ts"], kind="stable").reset_index(drop=True)
    return add_features(rows)


def add_features(rows: pd.DataFrame) -> pd.DataFrame:
    """Rows must be sorted by customer, then time.

    Window sums are done with prefix sums over one sorted key (customer, seconds), so 10M rows
    take seconds rather than a groupby-rolling's minutes. Each row sees the rows before it in
    the window plus itself - the same thing scoring-service sees, because it records a
    transaction in the ledger before the detectors run.
    """
    seconds = ((rows["ts"] - rows["ts"].min()).dt.total_seconds()).astype(np.int64).values
    customer_code = pd.factorize(rows["customer"])[0].astype(np.int64)
    key = customer_code * 10**9 + seconds          # sorted, because rows are
    idx = np.arange(len(rows))

    amount = rows["amount_eur"].values
    is_cash = (rows["channel"] == "CASH").values.astype(np.int64)
    is_credit = rows["is_credit"].values.astype(np.int64)

    def window(values, span):
        # (t - span, t]: the first row strictly after t - span, through this row.
        start = np.searchsorted(key, key - span, side="right")
        prefix = np.concatenate([[0], np.cumsum(values)])
        return prefix[idx + 1] - prefix[start]

    ones = np.ones(len(rows), dtype=np.int64)
    count_7d = window(ones, WEEK)
    sum_7d = window(amount, WEEK)
    earlier = count_7d - 1

    rows["log_amount_eur"] = np.log1p(amount)
    rows["channel_cash"] = is_cash
    rows["channel_card"] = (rows["channel"] == "CARD").astype(np.int64)
    rows["channel_transfer"] = (rows["channel"] == "TRANSFER").astype(np.int64)
    rows["hour_of_day"] = rows["ts"].dt.hour
    rows["log_count_24h"] = np.log1p(window(ones, DAY))
    rows["log_sum_24h"] = np.log1p(window(amount, DAY))
    rows["log_credit_count_24h"] = np.log1p(window(is_credit, DAY))
    rows["log_cash_count_24h"] = np.log1p(window(is_cash, DAY))
    rows["log_count_7d"] = np.log1p(count_7d)
    mean_earlier = (sum_7d - amount) / np.maximum(earlier, 1)
    rows["log_amount_vs_7d"] = np.where(earlier > 0, np.log1p(amount) - np.log1p(mean_earlier), 0.0)

    # The first row of each (customer, counterparty) pair. Summed over a window, that is how
    # many counterparties the customer met for the first time inside it.
    known = rows["counterparty"].notna()
    first = (rows.groupby(["customer", "counterparty"], dropna=True).cumcount() == 0) & known
    rows["is_new_counterparty"] = first.astype(np.int64)
    rows["log_new_counterparties_24h"] = np.log1p(window(first.values.astype(np.int64), DAY))
    return rows


def time_split(rows: pd.DataFrame, train_fraction: float = 0.7):
    """Train on the earlier days, test on the later ones - never a random split, which would
    let the model see a laundering chain's later payments while being tested on its earlier
    ones."""
    cutoff = rows["ts"].quantile(train_fraction)
    return rows[rows["ts"] <= cutoff], rows[rows["ts"] > cutoff], cutoff


def matrix(rows: pd.DataFrame) -> np.ndarray:
    return rows[FEATURES].to_numpy(dtype=np.float32)
