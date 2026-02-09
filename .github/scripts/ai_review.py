import os
import requests
from openai import OpenAI

client = OpenAI(api_key=os.environ["OPENAI_API_KEY"])

REPO = os.environ["REPO"]
PR_NUMBER = os.environ["PR_NUMBER"]
GITHUB_TOKEN = os.environ["GITHUB_TOKEN"]

with open("pr.diff", "r", encoding="utf-8", errors="ignore") as f:
    diff = f.read()

if not diff.strip():
    print("Empty diff")
    exit(0)

SYSTEM_PROMPT = """
Ты — строгий Senior Java Reviewer.
Стек: Java 21, Spring Boot, JPA, микросервисы.

Формат ответа:
1) Summary
2) Blocking Issues (P0/P1)
3) Improvements (P2)
4) Suggested Patch
"""

response = client.responses.create(
    model="gpt-4.1-mini",
    input=[
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": "Сделай ревью этого diff:\n\n" + diff},
    ],
)

review = response.output_text

url = f"https://api.github.com/repos/{REPO}/issues/{PR_NUMBER}/comments"

headers = {
    "Authorization": f"Bearer {GITHUB_TOKEN}",
    "Accept": "application/vnd.github+json",
}

requests.post(url, headers=headers, json={"body": "🤖 AI Review\n\n" + review})
