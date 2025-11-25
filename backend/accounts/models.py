from django.db import models

# Create your models here.
#회원 유형 
class UserType(models.Model):
    user_type_id = models.AutoField(primary_key=True)
    type_name = models.CharField(max_length=50, unique=True, null=False)
    type_description = models.TextField(null=True, blank=True)

    def __str__(self):
        return self.type_name

#회원 정보
class User(models.Model):
    user_num = models.AutoField(primary_key=True)
    user_type = models.ForeignKey(UserType, on_delete=models.PROTECT, null=False)
    login_id = models.CharField(max_length=50, unique=True, null=False)
    password = models.CharField(max_length=255, null=False)
    date_of_birth = models.DateField(null=True, blank=True) # PDF에 NN=Y(Yes)가 아니므로 True
    name = models.CharField(max_length=50, null=False)

    def __str__(self):
        return self.name
    
#회원 상세정보
class UserDetailInfo(models.Model):
    user = models.OneToOneField(User, on_delete=models.CASCADE, primary_key=True)
    region = models.CharField(max_length=100, null=False)
    income_bracket = models.IntegerField(null=True, blank=True) # NULL 허용

    def __str__(self):
        return f"{self.user.name}의 상세정보"