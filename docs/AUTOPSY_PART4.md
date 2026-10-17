# AUTOPSY PART 4 (connectors / n8n / web browsers / learning)
Traced: ConnectorApi, ConnectorStore, ConnectorRules, NovaService connector flow, WebBrowsers/WebSources, LearnStore/learnBegin, TeachStore.
Fixed (proven by reading the code):
1. n8n "executions <workflow name>" sent the NAME as workflowId (API wants an id) -> silently empty. Now resolves the name, honest error if unknown.
2. Learning said "yaad rakh liya" even when LearnStore.addKept returned null (topic already stored, nothing saved). Now says so honestly.
Checked, no bug: webhook without token (NO_TOKEN marker), permission gates, https-only/no-redirect http, web hosts allowlist matches all request URLs, offline answers, learn queue offline path.
Not tested on phone, Kotlin compile NOT RUN. Test: tools/test_autopsy_part4_contract.py
