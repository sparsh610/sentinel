# ml — model training

Python lives here and **only** here. It trains models and exports `.onnx` files; it is not a
runtime component of Sentinel. The contract with `scoring-service` is the `.onnx` file plus its
feature-order manifest — nothing else.

## Models

| Notebook | Model | Kind | Job |
|---|---|---|---|
| `01_fraud_supervised.ipynb` | XGBoost | Supervised | Classify against *known* fraud patterns |
| `02_anomaly_unsupervised.ipynb` | Isolation Forest | Unsupervised | Flag behaviour unlike anything normal, including patterns nobody has labelled |
| `03_segmentation.ipynb` | KMeans | Unsupervised | Peer groups, so "unusual" is judged within a segment |

Why all three, and why PR-AUC rather than accuracy: `../docs/design-decisions.md` §3.

## Data

The [Kaggle credit-card fraud dataset](https://www.kaggle.com/datasets/mlg-ulb/creditcardfraud)
— 284,807 transactions, 492 of them fraudulent (0.17%), features anonymised by PCA.

It is **not committed**; `ml/data/` is gitignored. Download it manually, or:

```bash
pip install kaggle          # then put your API token in ~/.kaggle/kaggle.json
kaggle datasets download -d mlg-ulb/creditcardfraud -p data --unzip
```

## Setup

```bash
python -m venv .venv
.venv\Scripts\activate         # Windows
pip install -r requirements.txt
jupyter lab
```

> **Note on the local Python.** This machine has Python 3.14. Some of these libraries may not
> publish wheels for it yet, which means a slow source build or an outright failure. If
> `pip install` struggles, create the venv from a Python 3.11 or 3.12 instead — nothing here
> needs a new language feature. Google Colab is also a perfectly good option for the notebooks;
> only the exported `.onnx` files matter downstream.

## Exporting

Each notebook ends by writing two files into `models/`:

```
models/fraud_xgboost.onnx            the model
models/fraud_xgboost.features.json   the feature order, as a JSON array of names
```

The manifest is not optional. `scoring-service` asserts that its Java feature builder produces
the same order, because a silent feature-order mismatch yields plausible but wrong scores —
the worst failure mode in this system.

`models/*.onnx` is gitignored. Regenerate by running the notebooks; document any
hyperparameters worth keeping in the notebook itself.
