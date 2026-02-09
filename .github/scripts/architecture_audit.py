import os
import requests
from openai import OpenAI

client = OpenAI(api_key=os.environ["OPENAI_API_KEY"])

REPO = os.environ["REPO"]
GITHUB_TOKEN = os.environ["GITHUB_TOKEN"]

with open("project_snapshot.txt", "r", encoding="utf-8", errors="ignore") as f:
    content = f.read()

MAX_CHARS = 150000
if len(content) > MAX_CHARS:
    content = content[:MAX_CHARS] + "\n\n[TRUNCATED]\n"

SYSTEM_PROMPT = """
Ты — Senior Java Architect.
Специализация: Spring Boot, JPA, микросервисы, Kafka, Eureka, Gateway.

Сделай архитектурный аудит проекта.

Проверь:
1) Границы микросервисов
2) Нарушения слоёв (Controller/Service/Repository)
3) DTO vs Entity
4) Транзакции
5) Потенциальные N+1
6) Зависимости между модулями
7) Антипаттерны

Формат:
1) Общая оценка (0-10)
2) Критические проблемы
3) Архитектурные риски
4) Улучшения
5) Конкретные рекомендации
"""

response = client.responses.create(
    model="gpt-4.1-mini",
    input=[
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": content},
    ],
)

report = response.output_text

url = f"https://api.github.com/repos/{REPO}/issues"

headers = {
    "Authorization": f"Bearer {GITHUB_TOKEN}",
    "Accept": "application/vnd.github+json",
}

data = {
    "title": "🤖 Architecture Audit Report",
    "body": report
}

requests.post(url, headers=headers, json=data)
