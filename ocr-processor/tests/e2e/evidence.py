"""Print the input, status, and output of a case so the Tests tab can show them."""
import re


def _clip(value):
    text = " ".join(str(value).split())
    if not text:
        return "(empty)"
    return text[:320] + ("…" if len(text) > 320 else "")


def visible(body):
    """Turn an HTML page into the text a person would read. JSON is left as JSON."""
    text = "" if body is None else str(body)
    stripped = text.lstrip()
    if stripped.startswith("{") or stripped.startswith("["):
        return " ".join(text.split())
    text = re.sub(r"(?is)<(script|style)[^>]*>.*?</\1>", " ", text)
    text = re.sub(r"(?s)<[^>]+>", " ", text)
    return " ".join(text.split())


def record(executed, validated, observed=None):
    text = f"Executed: {_clip(executed)}. Validated: {_clip(validated)}"
    if observed is not None:
        text += f". Observed: {_clip(observed)}"
    print("EVIDENCE: " + text + ".")


def saw(fact):
    print(f"EVIDENCE: {fact}")
