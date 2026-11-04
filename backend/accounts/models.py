from django.contrib.auth.models import AbstractBaseUser, BaseUserManager, PermissionsMixin
from django.db import models


class UserType(models.Model):
    user_type_id = models.AutoField(primary_key=True)
    type_name = models.CharField(max_length=50, unique=True, null=False)
    type_description = models.TextField(null=True, blank=True)

    def __str__(self):
        return self.type_name


class UserManager(BaseUserManager):
    use_in_migrations = True

    def create_user(self, login_id, email, password, name, user_type, date_of_birth=None, **extra):
        if not login_id:
            raise ValueError("아이디가 필요합니다.")
        if not email:
            raise ValueError("이메일이 필요합니다.")
        user = self.model(
            login_id=login_id,
            email=self.normalize_email(email),
            name=name,
            user_type=user_type,
            date_of_birth=date_of_birth,
            **extra,
        )
        user.set_password(password)
        user.save(using=self._db)
        return user

    def create_superuser(self, login_id, email, password, name="관리자", **extra):
        user_type, _created = UserType.objects.get_or_create(type_name="관리자")
        user = self.create_user(
            login_id=login_id,
            email=email,
            password=password,
            name=name,
            user_type=user_type,
            **extra,
        )
        user.is_staff = True
        user.is_superuser = True
        user.save(using=self._db)
        return user


class User(AbstractBaseUser, PermissionsMixin):
    """Django 인증 사용자.

    비밀번호는 AbstractBaseUser.set_password가 만든 해시만 저장합니다.
    예전 CharField에 평문을 넣어 비교하는 방식은 사용하지 않습니다.
    """

    user_num = models.AutoField(primary_key=True)
    user_type = models.ForeignKey(UserType, on_delete=models.PROTECT, null=False)
    login_id = models.CharField(max_length=50, unique=True, null=False)
    email = models.EmailField(max_length=254, unique=True)
    date_of_birth = models.DateField(null=True, blank=True)
    name = models.CharField(max_length=50, null=False)
    is_active = models.BooleanField(default=True)
    is_staff = models.BooleanField(default=False)

    USERNAME_FIELD = "login_id"
    REQUIRED_FIELDS = ["email", "name"]

    objects = UserManager()

    def __str__(self):
        return self.name


class UserDetailInfo(models.Model):
    user = models.OneToOneField(User, on_delete=models.CASCADE, primary_key=True)
    region = models.CharField(max_length=100, null=False)
    income_bracket = models.IntegerField(null=True, blank=True)

    def __str__(self):
        return f"{self.user.name}의 상세정보"
