#!/usr/bin/env python3
"""
Evaluation script for the thesis
"Hybrid Rule-Based and Machine Learning Anomaly Detection for Network Traffic Monitoring".

It answers RQ1-RQ5 on the CICIDS2017 flow data and writes CSV tables and PNG charts
into the output folder (default: results/).

Usage
-----
    pip install pandas numpy scikit-learn matplotlib
    python run_evaluation.py --data /path/to/CICIDS2017_csv_folder
    python run_evaluation.py --data /path/to/folder --quick        # small test run (minutes)

The folder must contain the CICIDS2017 CSV files (either the "MachineLearningCVE" or the
"TrafficLabelling" version). Labels are used ONLY for scoring, never for training.
"""
import argparse, glob, json, os, platform, re, sys, time, warnings
import numpy as np
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import sklearn
from sklearn.ensemble import IsolationForest
from sklearn.neighbors import LocalOutlierFactor
from sklearn.svm import OneClassSVM
from sklearn.preprocessing import StandardScaler
from sklearn.model_selection import train_test_split
from sklearn.metrics import (precision_score, recall_score, f1_score, roc_auc_score,
                             roc_curve, confusion_matrix)

warnings.filterwarnings("ignore")

# ---- rule parameters: identical to the Spring Boot DetectionService defaults
BYTES_MEDIUM = 200_000
BYTES_HIGH = 500_000
SUSPICIOUS_PORTS = (21, 23, 3389)
ALGOS = ["isolation_forest", "local_outlier_factor", "one_class_svm"]
NICE = {"isolation_forest": "Isolation Forest", "local_outlier_factor": "Local Outlier Factor",
        "one_class_svm": "One-Class SVM"}


# ------------------------------------------------------------------ data loading
def norm(c):
    return re.sub(r"\s+", " ", str(c)).strip().lower()


def load_pool(folder, benign_pool, attack_cap_per_class, seed):
    files = sorted(glob.glob(os.path.join(folder, "**", "*.csv"), recursive=True))
    if not files:
        sys.exit(f"No CSV files found under {folder}")
    rng = np.random.default_rng(seed)
    parts = []
    for f in files:
        df = pd.read_csv(f, low_memory=False, encoding="latin-1")
        df.columns = [norm(c) for c in df.columns]
        if "label" not in df.columns:
            print("skip (no label column):", os.path.basename(f)); continue
        df = df.replace([np.inf, -np.inf], np.nan)
        num = [c for c in df.columns if c != "label" and pd.api.types.is_numeric_dtype(df[c])]
        keep_ip = [c for c in ("source ip", "src ip") if c in df.columns]
        df = df[num + keep_ip + ["label"]].dropna(subset=num)
        df[num] = df[num].astype("float32")
        df["label"] = df["label"].astype(str).str.strip().str.replace("�", "-", regex=False)
        benign = df[df["label"].str.upper() == "BENIGN"]
        attack = df[df["label"].str.upper() != "BENIGN"]
        take = min(len(benign), max(1, benign_pool * 2 // len(files)))
        benign = benign.sample(take, random_state=int(rng.integers(1e9)))
        parts += [benign, attack]
        print(f"  loaded {os.path.basename(f)}: {len(df):,} rows")
    pool = pd.concat(parts, ignore_index=True)
    before = len(pool)
    pool = pool.drop_duplicates()
    print(f"pool: {before:,} rows, {len(pool):,} after removing duplicates")
    # cap per attack class to keep the pool balanced across attack types
    out = [pool[pool["label"].str.upper() == "BENIGN"]]
    for lab, g in pool[pool["label"].str.upper() != "BENIGN"].groupby("label"):
        out.append(g.sample(min(len(g), attack_cap_per_class), random_state=seed))
    pool = pd.concat(out, ignore_index=True)
    return pool


def find_col(cols, patterns):
    for p in patterns:
        for c in cols:
            if re.fullmatch(p, c):
                return c
    return None


def rule_severity(df, cols):
    """0 = no alert, 1 = MEDIUM, 2 = HIGH (same logic as DetectionService, applied to flows)."""
    fwd = find_col(cols, [r"total length of fwd packets?", r"totlen fwd pkts"])
    bwd = find_col(cols, [r"total length of bwd packets?", r"totlen bwd pkts"])
    port = find_col(cols, [r"destination port", r"dst port"])
    if fwd is None or bwd is None or port is None:
        sys.exit("Could not find byte / port columns. Columns seen: " + ", ".join(cols[:40]))
    total_bytes = df[fwd].astype("float64") + df[bwd].astype("float64")
    sev = np.zeros(len(df), dtype=int)
    vol = total_bytes > BYTES_MEDIUM
    sev[vol.values] = 1
    sev[(total_bytes > BYTES_HIGH).values] = 2
    sus = df[port].isin(SUSPICIOUS_PORTS).values & ~vol.values
    sev[sus] = 1
    return sev


def feature_matrix(df, cols):
    X = df[cols].to_numpy(dtype="float64")
    return np.sign(X) * np.log1p(np.abs(X))


# ------------------------------------------------------------------ models
def build(algo, contamination, seed):
    if algo == "isolation_forest":
        return IsolationForest(n_estimators=100, contamination=contamination, random_state=seed, n_jobs=-1)
    if algo == "local_outlier_factor":
        return LocalOutlierFactor(n_neighbors=20, contamination=contamination, novelty=True, n_jobs=-1)
    return OneClassSVM(nu=contamination, kernel="rbf", gamma="scale")


def fit_score(algo, contamination, seed, Xtr, Xte):
    """returns (pred_anomaly bool array, anomaly_score array [higher = more anomalous], train_s, predict_s)"""
    sc = StandardScaler().fit(Xtr)
    a, b = sc.transform(Xtr), sc.transform(Xte)
    m = build(algo, contamination, seed)
    t0 = time.perf_counter(); m.fit(a); t1 = time.perf_counter()
    pred = m.predict(b) == -1
    score = -m.decision_function(b)
    t2 = time.perf_counter()
    return pred, score, t1 - t0, t2 - t1, (sc, m)


def metrics(y, pred, score=None):
    d = {"precision": precision_score(y, pred, zero_division=0),
         "recall": recall_score(y, pred, zero_division=0),
         "f1": f1_score(y, pred, zero_division=0)}
    tn, fp, fn, tp = confusion_matrix(y, pred, labels=[0, 1]).ravel()
    d["fpr"] = fp / max(1, fp + tn)
    d["tp"], d["fp"], d["fn"], d["tn"] = int(tp), int(fp), int(fn), int(tn)
    d["roc_auc"] = roc_auc_score(y, score) if score is not None and len(set(y)) > 1 else np.nan
    return d


def pct_rank(x):
    return pd.Series(x).rank(pct=True).to_numpy()


def summarise(rows, keys):
    df = pd.DataFrame(rows).drop(columns=["seed"])
    num = [c for c in df.columns if c not in keys and pd.api.types.is_numeric_dtype(df[c])]
    g = df.groupby(keys, sort=False)[num]
    mean, std = g.mean().add_suffix("_mean"), g.std().fillna(0).add_suffix("_std")
    return pd.concat([mean, std], axis=1).reset_index()


# ------------------------------------------------------------------ main
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True, help="folder with CICIDS2017 CSV files")
    ap.add_argument("--out", default="results")
    ap.add_argument("--seeds", type=int, default=5)
    ap.add_argument("--benign-n", type=int, default=100_000, help="benign flows per experiment")
    ap.add_argument("--attack-frac", type=float, default=0.05, help="share of attacks in the experiment data")
    ap.add_argument("--max-train", type=int, default=20_000, help="training rows per model (LOF/OCSVM are slow)")
    ap.add_argument("--contamination", type=float, default=0.05, help="main contamination value")
    ap.add_argument("--sweep", type=float, nargs="+", default=[0.01, 0.02, 0.05, 0.10, 0.20])
    ap.add_argument("--latency-n", type=int, default=1000)
    ap.add_argument("--quick", action="store_true", help="small, fast test run")
    a = ap.parse_args()
    if a.quick:
        a.seeds, a.benign_n, a.max_train, a.latency_n = 2, 20_000, 5_000, 300
    os.makedirs(a.out, exist_ok=True)
    t_start = time.time()

    with open(os.path.join(a.out, "environment.txt"), "w") as f:
        f.write(f"Python {platform.python_version()}\nscikit-learn {sklearn.__version__}\n"
                f"numpy {np.__version__}\npandas {pd.__version__}\nOS {platform.platform()}\n"
                f"CPU {platform.processor() or platform.machine()} ({os.cpu_count()} logical cores)\n"
                f"arguments {json.dumps(vars(a))}\n")

    print("Loading data ...")
    pool = load_pool(a.data, a.benign_n, attack_cap_per_class=max(2000, int(a.benign_n * a.attack_frac * 3)), seed=1)
    cols_all = [c for c in pool.columns if c not in ("label", "source ip", "src ip")]
    # drop constant columns
    cols = [c for c in cols_all if pool[c].nunique() > 1]
    ip_col = find_col(pool.columns, [r"source ip", r"src ip"])
    print(f"{len(cols)} numeric features, source-IP column: {ip_col}")
    pool["attack"] = (pool["label"].str.upper() != "BENIGN").astype(int)

    # Figure 4: class distribution of the pool
    cd = pool["label"].value_counts()
    cd.rename_axis("label").reset_index(name="flows").to_csv(os.path.join(a.out, "class_distribution.csv"), index=False)
    fig, ax = plt.subplots(figsize=(7.5, 3.6))
    ax.bar(range(len(cd)), cd.values, color=["#8FAADC" if l.upper() == "BENIGN" else "#C55A11" for l in cd.index])
    ax.set_yscale("log"); ax.set_xticks(range(len(cd))); ax.set_xticklabels(cd.index, rotation=60, ha="right", fontsize=7)
    ax.set_ylabel("flows (log scale)"); plt.tight_layout()
    plt.savefig(os.path.join(a.out, "fig04_class_distribution.png"), dpi=220); plt.close()

    n_att = int(a.benign_n * a.attack_frac / (1 - a.attack_frac))
    ablation, algos, sweep, encoding, per_attack = [], [], [], [], []
    keep0 = {}

    for seed in range(a.seeds):
        print(f"=== seed {seed} ===")
        rng = np.random.default_rng(100 + seed)
        ben = pool[pool["attack"] == 0]; att = pool[pool["attack"] == 1]
        ben = ben.sample(min(a.benign_n, len(ben)), random_state=100 + seed)
        att = att.sample(min(n_att, len(att)), random_state=100 + seed)
        data = pd.concat([ben, att]).sample(frac=1, random_state=seed).reset_index(drop=True)
        tr, te = train_test_split(data, test_size=0.5, stratify=data["attack"], random_state=seed)
        if len(tr) > a.max_train:
            tr = tr.sample(a.max_train, random_state=seed)
        ytr, yte = tr["attack"].to_numpy(), te["attack"].to_numpy()
        Xtr, Xte = feature_matrix(tr, cols), feature_matrix(te, cols)
        sev = rule_severity(te, list(te.columns))
        if seed == 0:
            pd.DataFrame({"set": ["train", "test"],
                          "benign": [int((ytr == 0).sum()), int((yte == 0).sum())],
                          "attack": [int(ytr.sum()), int(yte.sum())],
                          "total": [len(ytr), len(yte)]}).to_csv(os.path.join(a.out, "dataset_size.csv"), index=False)

        # --- RQ1 ablation (Isolation Forest as the ML stage)
        pred_ml, sc_ml, _, _, _ = fit_score("isolation_forest", a.contamination, seed, Xtr, Xte)
        rule_any, rule_high = sev >= 1, sev >= 2
        cascade_pred = rule_any & pred_ml                      # as implemented: ML re-scores flagged traffic only
        cascade_score = np.where(rule_any, pct_rank(sc_ml), -1.0)
        union_pred = rule_any | pred_ml
        union_score = sev + pct_rank(sc_ml)
        configs = {
            "Heuristic only (any alert)": (rule_any, sev.astype(float)),
            "Heuristic only (HIGH only)": (rule_high, sev.astype(float)),
            "ML only (raw flow features)": (pred_ml, sc_ml),
            "Hybrid cascade (rules then ML)": (cascade_pred, cascade_score),
            "Hybrid union (rules or ML)": (union_pred, union_score),
        }
        for name, (p, s) in configs.items():
            ablation.append({"configuration": name, "seed": seed, **metrics(yte, p, s)})
            if seed == 0:
                for lab, g in te.assign(pred=p).groupby("label"):
                    if lab.upper() != "BENIGN":
                        per_attack.append({"configuration": name, "attack": lab, "flows": len(g),
                                           "recall": float(g["pred"].mean())})

        # --- RQ3 algorithms
        for algo in ALGOS:
            p, s, ttr, tpr, _ = fit_score(algo, a.contamination, seed, Xtr, Xte)
            algos.append({"algorithm": NICE[algo], "seed": seed, **metrics(yte, p, s),
                          "train_s": ttr, "predict_s": tpr})
            if seed == 0:
                keep0[algo] = (yte, s, p)

        # --- RQ2 contamination sweep (Isolation Forest)
        for c in a.sweep:
            p, s, _, _, _ = fit_score("isolation_forest", c, seed, Xtr, Xte)
            sweep.append({"contamination": c, "seed": seed, **metrics(yte, p, s)})

        # --- RQ4 encoding of the source IP (needs the TrafficLabelling csv files with the Source IP column)
        if ip_col is not None:
            def ipnum(s):
                def one(x):
                    try:
                        o = [int(v) for v in str(x).split(".")]
                        return sum(v * 256 ** (3 - i) for i, v in enumerate(o)) % 2147483647
                    except Exception:
                        return 0
                return s.map(one).to_numpy(dtype="float64")
            def enc_hash(d, ref): return ipnum(d[ip_col])
            def enc_freq(d, ref):
                f = ref[ip_col].value_counts(normalize=True); return d[ip_col].map(f).fillna(0).to_numpy()
            def enc_subnet(d, ref):
                sub = lambda x: ".".join(str(x).split(".")[:3])
                f = ref[ip_col].map(sub).value_counts(normalize=True); return d[ip_col].map(sub).map(f).fillna(0).to_numpy()
            for name, fn in [("no IP feature", None), ("hashed IP (as in MLFeatureMapper)", enc_hash),
                             ("IP frequency", enc_freq), ("/24 subnet frequency", enc_subnet)]:
                A, B = Xtr, Xte
                if fn is not None:
                    A = np.column_stack([Xtr, fn(tr, tr)]); B = np.column_stack([Xte, fn(te, tr)])
                p, s, _, _, _ = fit_score("isolation_forest", a.contamination, seed, A, B)
                encoding.append({"encoding": name, "seed": seed, **metrics(yte, p, s)})

        # --- RQ5 runtime (seed 0 only): per-record latency, in-process
        if seed == 0:
            n = min(a.latency_n, len(te))
            sub = te.sample(n, random_state=0)
            Xs = feature_matrix(sub, cols)
            sc_, m_ = fit_score("isolation_forest", a.contamination, seed, Xtr, Xte[:10])[4]
            Xs_scaled = sc_.transform(Xs)
            sev_s = rule_severity(sub, list(sub.columns))
            # rules: python re-implementation of the same checks, per record
            fwd = find_col(list(sub.columns), [r"total length of fwd packets?", r"totlen fwd pkts"])
            bwd = find_col(list(sub.columns), [r"total length of bwd packets?", r"totlen bwd pkts"])
            port = find_col(list(sub.columns), [r"destination port", r"dst port"])
            recs = sub[[fwd, bwd, port]].to_numpy()
            def rules_one(r):
                b = r[0] + r[1]
                if b > BYTES_MEDIUM: return 2 if b > BYTES_HIGH else 1
                return 1 if int(r[2]) in SUSPICIOUS_PORTS else 0
            lat = {"Rules only": [], "ML only": [], "Hybrid cascade": []}
            for i in range(n):
                t = time.perf_counter(); rules_one(recs[i]); lat["Rules only"].append((time.perf_counter() - t) * 1e3)
                t = time.perf_counter(); m_.predict(Xs_scaled[i:i + 1]); lat["ML only"].append((time.perf_counter() - t) * 1e3)
                t = time.perf_counter()
                if rules_one(recs[i]) > 0:
                    m_.predict(Xs_scaled[i:i + 1])
                lat["Hybrid cascade"].append((time.perf_counter() - t) * 1e3)
            rt = []
            for k, v in lat.items():
                v = np.array(v)
                rt.append({"configuration": k, "median_ms": np.median(v), "p95_ms": np.percentile(v, 95),
                           "mean_ms": v.mean(), "records_per_s": 1000 / v.mean(),
                           "ml_calls": n if k == "ML only" else (int((sev_s > 0).sum()) if k == "Hybrid cascade" else 0),
                           "records": n})
            pd.DataFrame(rt).to_csv(os.path.join(a.out, "runtime.csv"), index=False)
            keep0["lat"] = lat

    # ------------------------------------------------------------------ write tables
    O = lambda n: os.path.join(a.out, n)
    ab = summarise(ablation, ["configuration"]); ab.to_csv(O("ablation.csv"), index=False)
    al = summarise(algos, ["algorithm"]); al.to_csv(O("algorithms.csv"), index=False)
    sw = summarise(sweep, ["contamination"]); sw.to_csv(O("contamination.csv"), index=False)
    pd.DataFrame(ablation).to_csv(O("ablation_per_seed.csv"), index=False)
    pd.DataFrame(algos).to_csv(O("algorithms_per_seed.csv"), index=False)
    pd.DataFrame(sweep).to_csv(O("contamination_per_seed.csv"), index=False)
    if encoding:
        summarise(encoding, ["encoding"]).to_csv(O("encoding.csv"), index=False)
    pd.DataFrame(per_attack).to_csv(O("per_attack_recall.csv"), index=False)

    # ------------------------------------------------------------------ charts
    # Fig 16 F1 of the configurations
    fig, ax = plt.subplots(figsize=(7.5, 3.6))
    ax.bar(range(len(ab)), ab["f1_mean"], yerr=ab["f1_std"], color="#8FAADC", capsize=3)
    ax.set_xticks(range(len(ab))); ax.set_xticklabels([c.replace(" (", "\n(") for c in ab["configuration"]], fontsize=7)
    ax.set_ylabel("F1-score"); ax.set_ylim(0, 1); plt.tight_layout()
    plt.savefig(O("fig16_f1_configurations.png"), dpi=220); plt.close()
    # Fig 17 ROC
    fig, ax = plt.subplots(figsize=(5, 4))
    for algo in ALGOS:
        y, s, _ = keep0[algo]; fpr, tpr, _ = roc_curve(y, s)
        ax.plot(fpr, tpr, label=f"{NICE[algo]} (AUC {roc_auc_score(y, s):.3f})")
    ax.plot([0, 1], [0, 1], "--", color="grey"); ax.set_xlabel("False positive rate"); ax.set_ylabel("True positive rate")
    ax.legend(fontsize=8); plt.tight_layout(); plt.savefig(O("fig17_roc.png"), dpi=220); plt.close()
    # Fig 18 confusion matrices
    fig, axs = plt.subplots(1, 3, figsize=(9, 3))
    for ax, algo in zip(axs, ALGOS):
        y, s, p = keep0[algo]; cm = confusion_matrix(y, p, labels=[0, 1])
        ax.imshow(cm, cmap="Blues"); ax.set_title(NICE[algo], fontsize=9)
        for i in range(2):
            for j in range(2):
                ax.text(j, i, f"{cm[i, j]:,}", ha="center", va="center", fontsize=8, color="black")
        ax.set_xticks([0, 1]); ax.set_yticks([0, 1]); ax.set_xticklabels(["normal", "attack"]); ax.set_yticklabels(["normal", "attack"])
        ax.set_xlabel("predicted", fontsize=8); ax.set_ylabel("actual", fontsize=8)
    plt.tight_layout(); plt.savefig(O("fig18_confusion.png"), dpi=220); plt.close()
    # Fig 19 contamination
    fig, ax = plt.subplots(figsize=(6.5, 3.6))
    for k, lab in [("precision", "precision"), ("recall", "recall"), ("fpr", "false positive rate")]:
        ax.errorbar(sw["contamination"], sw[k + "_mean"], yerr=sw[k + "_std"], marker="o", label=lab, capsize=2)
    ax.set_xlabel("contamination"); ax.set_ylabel("value"); ax.legend(fontsize=8); plt.tight_layout()
    plt.savefig(O("fig19_contamination.png"), dpi=220); plt.close()
    # Fig 20 latency
    fig, ax = plt.subplots(figsize=(6.5, 3.6))
    keys = list(keep0["lat"].keys()); ax.boxplot([keep0["lat"][k] for k in keys], showfliers=False); ax.set_xticklabels(keys)
    ax.set_ylabel("latency per record (ms)"); ax.set_yscale("log"); plt.tight_layout()
    plt.savefig(O("fig20_latency.png"), dpi=220); plt.close()

    print(f"\nDone in {(time.time() - t_start) / 60:.1f} min. Results are in: {os.path.abspath(a.out)}")
    print("Send me the whole results folder (zip it).")


if __name__ == "__main__":
    main()
