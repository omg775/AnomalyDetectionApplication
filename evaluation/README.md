# Evaluation (Chapter 5)

`run_evaluation.py` produces every table and figure of Chapter 5 from the CICIDS2017 flow data.
It is independent of the backend and the ML service: it applies the same heuristic rules
(200,000 and 500,000 bytes, ports 21, 23, 3389) and the same three algorithms directly in Python.
Labels are used only for scoring, never for training. Results are the mean and standard deviation
over five random seeds (0 to 4).

## Contents

| Path | Content |
|---|---|
| `run_evaluation.py` | The evaluation script |
| `requirements.txt` | Exact package versions used for the results in the thesis |
| `results/` | The results reported in the thesis: CSV tables, PNG figures and `environment.txt` (versions and arguments of the run) |

## Data

The dataset is not included because of its size (about 1.4 GB unpacked). Download the CSV version
of CICIDS2017 from the Canadian Institute for Cybersecurity, University of New Brunswick:
https://www.unb.ca/cic/datasets/ids-2017.html

Use the "TrafficLabelling" CSV files (they contain the Source IP column needed for the encoding
experiment, RQ4). "MachineLearningCVE" also works for the other experiments. Put all CSV files in
one folder; subfolders are fine.

## Rerun

Python 3.11 (the results were produced with 3.11.15).

```bash
cd evaluation
python3.11 -m venv .venv
source .venv/bin/activate            # Windows: .venv\Scripts\activate
pip install -r requirements.txt

# quick check that everything works (a few minutes)
python run_evaluation.py --data /path/to/cicids2017 --quick --out results_quick

# full run as in the thesis (about 20 to 60 minutes, about 8 GB RAM)
python run_evaluation.py --data /path/to/cicids2017 --out results_rerun
```

The full run with the default arguments is the one in the thesis; `results/environment.txt` lists
the exact arguments. Use a different `--out` folder so the original results are not overwritten.
Run `python run_evaluation.py --help` for all options.
