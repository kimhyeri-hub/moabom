import base64
import hashlib
import hmac
import json
import time

from django.conf import settings

TOKEN_TTL_SECONDS = 12 * 60 * 60


class TokenError(Exception):
    pass


def issue_token(user):
    secret = _secret()
    now = int(time.time())
    header = _b64encode(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode("utf-8"))
    payload = {
        "sub": str(user.user_num),
        "login_id": user.login_id,
        "iat": now,
        "exp": now + TOKEN_TTL_SECONDS,
    }
    payload_segment = _b64encode(json.dumps(payload, separators=(",", ":")).encode("utf-8"))
    signing = f"{header}.{payload_segment}".encode("ascii")
    signature = hmac.new(secret.encode("utf-8"), signing, hashlib.sha256).digest()
    return f"{header}.{payload_segment}.{_b64encode(signature)}"


def read_subject(token):
    secret = _secret()
    parts = (token or "").split(".")
    if len(parts) != 3 or any(part == "" for part in parts):
        raise TokenError("인증 정보가 유효하지 않습니다.")
    signing = f"{parts[0]}.{parts[1]}".encode("ascii")
    expected = hmac.new(secret.encode("utf-8"), signing, hashlib.sha256).digest()
    try:
        actual = _b64decode(parts[2])
    except (ValueError, TypeError) as exc:
        raise TokenError("인증 정보가 유효하지 않습니다.") from exc
    if not hmac.compare_digest(expected, actual):
        raise TokenError("인증 정보가 유효하지 않습니다.")
    try:
        header = json.loads(_b64decode(parts[0]))
        payload = json.loads(_b64decode(parts[1]))
    except (ValueError, TypeError, json.JSONDecodeError) as exc:
        raise TokenError("인증 정보가 유효하지 않습니다.") from exc
    if header.get("alg") != "HS256":
        raise TokenError("인증 정보가 유효하지 않습니다.")
    exp = payload.get("exp")
    subject = payload.get("sub")
    if not isinstance(exp, int) or not isinstance(subject, str) or subject == "":
        raise TokenError("인증 정보가 유효하지 않습니다.")
    if exp < int(time.time()):
        raise TokenError("인증 정보가 유효하지 않습니다.")
    return subject


def bearer_subject(request):
    header = request.headers.get("Authorization", "")
    if not header.startswith("Bearer "):
        raise TokenError("인증 정보가 없습니다.")
    token = header[7:].strip()
    if token == "":
        raise TokenError("인증 정보가 없습니다.")
    return read_subject(token)


def _secret():
    secret = getattr(settings, "MOABOM_AUTH_SECRET", "") or ""
    if secret.strip() == "":
        raise TokenError("인증 비밀키가 설정되지 않았습니다.")
    return secret


def _b64encode(raw):
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def _b64decode(value):
    padding = "=" * (-len(value) % 4)
    return base64.urlsafe_b64decode(value + padding)
