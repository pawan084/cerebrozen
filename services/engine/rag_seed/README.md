# rag_seed — a local RAG demo corpus (no S3)

Production ingests the SSKB/CSKB knowledge bases from S3 (`app/rag/ingest/`).
A dev box, an air-gapped deploy, or a first-run demo has no bucket — and an
**empty index means the coach improvises instead of grounding in evidence**.

This directory is a tiny, illustrative corpus that loads with **no S3**, via
`app/rag/seed_demo.py` (`python -m scripts.seed_demo_rag`). It mirrors the exact
S3 taxonomy, so **your real content drops into the same layout and loads the
same way** — swap these files, keep the folders.

```
rag_seed/
  sskb/                         # global knowledge (all tenants)
    sskb_concept/               # → source=concept   (Extract1 {SSKB_Concept}, CIM)
    sskb_microlearning/         # → micro_learning   (Extract6 {SSKB_MicroLearning})
    sskb_competency/            # → competency       (Extract8 {SSKB_Competencies})
    sskb_curated/               # → curated .xlsx    (structured; skipped by the demo seeder)
  cskb/                         # per-client knowledge ("Tuned to Your Culture")
    <orgId>/
      cskb_org_framework/       # → doc_type=frameworks    (Extract2 {CSKB_Framework})
      cskb_values/              # → doc_type=values        (Extract3 {CSKB_Values})
      cskb_competency/          # → doc_type=competencies  (Extract4 {CSKB_Competencies})
      cskb_learning_aid/        # → doc_type=learning_aids (Extract5 {CSKB_LearningAid})
```

**The content below is illustrative placeholder** — generic, evidence-informed
coaching material written for this repo, not any client's material. Replace it
with your own SSKB/CSKB corpus before shipping. Supported file types:
`.md .txt .pdf .docx .pptx` (`.xlsx` only under `sskb_curated/`).
