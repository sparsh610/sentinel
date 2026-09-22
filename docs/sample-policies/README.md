# Sample policy corpus

Synthetic documents for developing and demoing retrieval. **None of this is a real bank policy
or real regulation** - it is written to look structurally like one, with numbered clauses that a
citation can point at.

Real regulatory text (AMLD, PSD2, GDPR) is public and can be added here, but it is deliberately
not committed: the files are large, and licensing varies by publisher.

## Try it

```bash
# ingest
curl -F "file=@docs/sample-policies/internal-aml-policy.txt"      -F "classification=INTERNAL"      -F "title=Internal AML Policy"      http://localhost:8081/api/documents

# ask
curl -X POST http://localhost:8081/api/chat/ask      -H "Content-Type: application/json"      -d '{"question":"When does a structuring pattern have to be reported?"}'
```

A good answer cites AML-04.2 and AML-04.3 and states the 24-hour deadline.

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
