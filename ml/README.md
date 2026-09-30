# ml — model training

Python lives here and **only** here. It trains models and exports `.onnx` files; it is not a
runtime component of Sentinel. The contract with `scoring-service` is the `.onnx` file plus its
feature-order manifest — nothing else.

## Models

| Notebook | Model | Kind | Job |
|---|---|---|---|
| `01_fraud_supervised.ipynb` | XGBoost | Supervised | Score against *known* laundering patterns |
| `02_anomaly_unsupervised.ipynb` | Isolation Forest | Unsupervised | Flag behaviour unlike anything normal, including patterns nobody has labelled |
| `03_segmentation.ipynb` | KMeans | Unsupervised | Peer segments, and the Isolation Forest's threshold within each one |

Run them in that order — `03` reads the model `02` exported. Why all three, and why PR-AUC
rather than accuracy: `../docs/design-decisions.md` §3. What they achieve and where they fall
short: §14.

## Data

IBM's [Transactions for Anti-Money-Laundering](https://www.kaggle.com/datasets/ealtman2019/ibm-transactions-for-anti-money-laundering-aml),
the `HI-Small_Trans.csv` file — about 5M synthetic payments between accounts, 0.1% of them
laundering. Unlike an anonymised dataset, every column maps onto a Sentinel transaction, so
`scoring-service` can build the same features the models were trained on.

It is **not committed**; `ml/data/` is gitignored. Fetch it with `kagglehub` (installed by the
requirements; at the time of writing this public dataset downloads without a Kaggle login):

```bash
# from ml/, with the venv below active
mkdir data
python -c "import kagglehub, shutil; shutil.copy(kagglehub.dataset_download('ealtman2019/ibm-transactions-for-anti-money-laundering-aml', path='HI-Small_Trans.csv'), 'data/')"
```

## Features

`sentinel_features.py` turns payments into model rows and is shared by all three notebooks.
Its `FEATURES` list is mirrored by `scoring-service/.../model/ModelFeatures.java`; change one
and you must change the other, or the service refuses to load the models. The first notebook
run builds the features (about two minutes) and caches them as `data/HI-Small_Trans.features.parquet`.

## Setup

```bash
python -m venv .venv
.venv\Scripts\activate         # Windows
pip install -r requirements.txt
jupyter lab
```

Python 3.14 works with current wheels. Google Colab is also fine — only the exported files
matter downstream.

## Exporting

Each notebook ends by writing two files into `models/`:

```
models/fraud_xgboost.onnx            the model
models/fraud_xgboost.features.json   the feature order, as a JSON array of names
```

The manifest is not optional. `scoring-service` asserts that its Java feature builder produces
the same order, because a silent feature-order mismatch yields plausible but wrong scores —
the worst failure mode in this system.

The `.onnx` files also carry metadata of their own: the feature list (checked when the service
loads the model), a few recorded rows with Python's outputs (replayed by `OnnxParityTest` in
Java), and, in `segments_kmeans.onnx`, the per-segment anomaly thresholds.

`models/*.onnx` is gitignored. Regenerate by running the notebooks; keep notebook outputs
cleared before committing.
