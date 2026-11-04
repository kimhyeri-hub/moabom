import json
import re
from datetime import date, datetime

from django.contrib.auth import authenticate
from django.contrib.auth.password_validation import validate_password
from django.core.exceptions import ValidationError
from django.core.validators import validate_email
from django.db import IntegrityError, transaction
from django.http import JsonResponse
from django.utils.translation import override
from django.views.decorators.csrf import csrf_exempt

from .auth_tokens import TokenError, bearer_subject, issue_token
from .models import User, UserDetailInfo, UserType

LOGIN_ID_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_-]{3,49}$")


@csrf_exempt
def signup_api(request):
    if request.method != "POST":
        return JsonResponse({"message": "POST만 지원합니다."}, status=405)
    payload, error = _body(request)
    if error is not None:
        return error
    try:
        login_id = _login_id(payload.get("loginId"))
        email = _email(payload.get("email"))
        name = _name(payload.get("name"))
        password = _required_text(payload.get("password"), "비밀번호")
        born = _birth(payload.get("dateOfBirth"))
        region = _optional_region(payload.get("region"))
        income = _optional_income(payload.get("incomeBracket"))
    except ValueError as exc:
        return JsonResponse({"message": str(exc)}, status=400)

    if User.objects.filter(login_id=login_id).exists():
        return JsonResponse({"message": "이미 사용 중인 아이디입니다."}, status=409)
    if User.objects.filter(email=email).exists():
        return JsonResponse({"message": "이미 사용 중인 이메일입니다."}, status=409)

    user_type, _created = UserType.objects.get_or_create(type_name="일반회원")
    candidate = User(login_id=login_id, email=email, name=name, user_type=user_type, date_of_birth=born)
    try:
        with override("ko"):
            validate_password(password, user=candidate)
    except ValidationError as exc:
        return JsonResponse({"message": " ".join(exc.messages)}, status=400)

    try:
        with transaction.atomic():
            user = User.objects.create_user(
                login_id=login_id,
                email=email,
                password=password,
                name=name,
                user_type=user_type,
                date_of_birth=born,
            )
            if region is not None:
                UserDetailInfo.objects.create(user=user, region=region, income_bracket=income)
    except IntegrityError:
        return JsonResponse({"message": "이미 사용 중인 아이디 또는 이메일입니다."}, status=409)
    try:
        token = issue_token(user)
    except TokenError as exc:
        return JsonResponse({"message": str(exc)}, status=500)
    return JsonResponse({"token": token, "user": _public_user(user)}, status=201)


@csrf_exempt
def login_api(request):
    if request.method != "POST":
        return JsonResponse({"message": "POST만 지원합니다."}, status=405)
    payload, error = _body(request)
    if error is not None:
        return error
    login_id = str(payload.get("loginId") or "").strip()
    password = str(payload.get("password") or "")
    if login_id == "" or password == "":
        return JsonResponse({"message": "아이디와 비밀번호를 입력해 주세요."}, status=400)
    user = authenticate(request, username=login_id, password=password)
    if user is None or not user.is_active:
        return JsonResponse({"message": "아이디 또는 비밀번호가 올바르지 않습니다."}, status=401)
    try:
        token = issue_token(user)
    except TokenError as exc:
        return JsonResponse({"message": str(exc)}, status=500)
    return JsonResponse({"token": token, "user": _public_user(user)})


@csrf_exempt
def logout_api(request):
    if request.method != "POST":
        return JsonResponse({"message": "POST만 지원합니다."}, status=405)
    try:
        bearer_subject(request)
    except TokenError as exc:
        status = 500 if "비밀키" in str(exc) else 401
        return JsonResponse({"message": str(exc)}, status=status)
    return JsonResponse({"message": "로그아웃했습니다. 브라우저에 저장된 토큰을 삭제해야 요청이 중단됩니다."})


@csrf_exempt
def me_api(request):
    if request.method not in ("GET", "PATCH"):
        return JsonResponse({"message": "GET 또는 PATCH만 지원합니다."}, status=405)
    try:
        subject = bearer_subject(request)
    except TokenError as exc:
        status = 500 if "비밀키" in str(exc) else 401
        return JsonResponse({"message": str(exc)}, status=status)
    user = User.objects.filter(user_num=subject, is_active=True).first()
    if user is None:
        return JsonResponse({"message": "인증 정보가 유효하지 않습니다."}, status=401)
    if request.method == "GET":
        return JsonResponse({"user": _public_user(user)})
    payload, error = _body(request)
    if error is not None:
        return error
    try:
        if "name" in payload:
            user.name = _name(payload.get("name"))
        if "email" in payload:
            email = _email(payload.get("email"))
            if User.objects.exclude(pk=user.pk).filter(email=email).exists():
                return JsonResponse({"message": "이미 사용 중인 이메일입니다."}, status=409)
            user.email = email
        if "dateOfBirth" in payload:
            user.date_of_birth = _birth(payload.get("dateOfBirth"))
        region = _optional_region(payload.get("region")) if "region" in payload else None
        income = _optional_income(payload.get("incomeBracket")) if "incomeBracket" in payload else None
    except ValueError as exc:
        return JsonResponse({"message": str(exc)}, status=400)
    try:
        with transaction.atomic():
            user.save()
            if region is not None:
                UserDetailInfo.objects.update_or_create(
                    user=user,
                    defaults={"region": region, "income_bracket": income},
                )
    except IntegrityError:
        return JsonResponse({"message": "이미 사용 중인 이메일입니다."}, status=409)
    return JsonResponse({"user": _public_user(user)})


@csrf_exempt
def login_id_api(request):
    if request.method != "GET":
        return JsonResponse({"message": "GET만 지원합니다."}, status=405)
    try:
        login_id = _login_id(request.GET.get("loginId"))
    except ValueError as exc:
        return JsonResponse({"message": str(exc)}, status=400)
    return JsonResponse({"loginId": login_id, "available": not User.objects.filter(login_id=login_id).exists()})


def _public_user(user):
    return {
        "id": str(user.user_num),
        "loginId": user.login_id,
        "name": user.name,
        "email": user.email,
        "dateOfBirth": user.date_of_birth.isoformat() if user.date_of_birth else None,
    }


def _body(request):
    try:
        payload = json.loads(request.body.decode("utf-8") or "{}")
    except (UnicodeDecodeError, json.JSONDecodeError):
        return None, JsonResponse({"message": "JSON 본문을 확인해 주세요."}, status=400)
    if not isinstance(payload, dict):
        return None, JsonResponse({"message": "JSON 본문을 확인해 주세요."}, status=400)
    return payload, None


def _login_id(value):
    login_id = str(value or "").strip()
    if LOGIN_ID_PATTERN.fullmatch(login_id) is None:
        raise ValueError("아이디는 영문, 숫자, 밑줄, 하이픈만 사용할 수 있고 4자 이상 50자 이하여야 합니다.")
    return login_id


def _email(value):
    email = str(value or "").strip()
    try:
        validate_email(email)
    except ValidationError as exc:
        raise ValueError("이메일 형식을 확인해 주세요.") from exc
    return email


def _name(value):
    name = str(value or "").strip()
    if name == "" or len(name) > 50:
        raise ValueError("이름을 1자 이상 50자 이하로 입력해 주세요.")
    return name


def _required_text(value, label):
    text = str(value or "")
    if text.strip() == "":
        raise ValueError(f"{label}를 입력해 주세요.")
    return text


def _birth(value):
    text = str(value or "").strip()
    born = None
    for fmt in ("%Y%m%d", "%Y-%m-%d"):
        try:
            born = datetime.strptime(text, fmt).date()
            break
        except ValueError:
            continue
    if born is None or born.year < 1900 or born > date.today():
        raise ValueError("생년월일은 YYYYMMDD 형식의 실제 날짜여야 합니다.")
    return born


def _optional_region(value):
    if value is None or str(value).strip() == "":
        return None
    region = str(value).strip()
    if len(region) > 100:
        raise ValueError("지역은 100자 이하여야 합니다.")
    return region


def _optional_income(value):
    if value is None or value == "":
        return None
    try:
        income = int(value)
    except (TypeError, ValueError) as exc:
        raise ValueError("소득분위는 1부터 10 사이의 정수여야 합니다.") from exc
    if income < 1 or income > 10:
        raise ValueError("소득분위는 1부터 10 사이의 정수여야 합니다.")
    return income
