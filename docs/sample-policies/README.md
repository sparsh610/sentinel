# Sample policy corpus

Synthetic documents for developing and demoing retrieval. **None of this is a real bank policy
or real regulation** - it is written to look structurally like one, with numbered clauses that a
citation can point at.

Real regulatory text (AMLD, PSD2, GDPR) is public and can be added here, but it is deliberately
not committed: the files are large, and licensing varies by publisher.

## Try it

```bash
# ingest - uploading is for senior approvers, who decide the classification
SENIOR=$(scripts/sentinel-token.sh sam.senior sam)
curl -H "Authorization: Bearer $SENIOR" -F "file=@docs/sample-policies/internal-aml-policy.txt" \
     -F "classification=INTERNAL" -F "title=Internal AML Policy" http://localhost:8081/api/documents
curl -H "Authorization: Bearer $SENIOR" -F "file=@docs/sample-policies/confidential-sar-handbook.txt" \
     -F "classification=CONFIDENTIAL" -F "title=SAR Handling Procedure" http://localhost:8081/api/documents

# ask, as an analyst
ANALYST=$(scripts/sentinel-token.sh alex.analyst alex)
curl -X POST http://localhost:8081/api/chat/ask -H "Authorization: Bearer $ANALYST" \
     -H "Content-Type: application/json" \
     -d '{"question":"When does a structuring pattern have to be reported?"}'
```

A good answer cites AML-04.2 and AML-04.3 and states the 24-hour deadline.

`confidential-sar-handbook.txt` exists to show the document ACL. Ask *"How quickly must a
signed-off report be filed?"* as `alex.analyst` and the SAR procedure is never among the sources;
ask as `sam.senior` and it is, citing SAR-01.1.

## Questions worth keeping

These become the golden set for the evaluation page in week 8:

| Question | Should cite |
|---|---|
| When does a structuring pattern have to be reported? | AML-04.2, AML-04.3 |
| Who can submit a suspicious activity report? | AML-05.2 |
| How long must due diligence records be kept? | AML-06.1 |
| What triggers enhanced due diligence? | AML-02.1 |
| How long does an analyst have to disposition an alert? | AML-03.2 |
| What is the penalty for tipping off? | *nothing - the corpus does not say. The answer must be "I don't have a source for that."* |

That last row matters most. A RAG system that invents an answer there is broken, however good
the other five look.
