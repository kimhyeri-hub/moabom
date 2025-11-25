from django.db import models
from django.conf import settings
# from accounts.models import User # <- 다른 앱의 모델을 직접 import 하는 것보다 settings.AUTH_USER_MODEL 권장

# Create your models here.
# 4. 카테고리 테이블 (category)
class Category(models.Model):
    category_num = models.AutoField(primary_key=True)
    category_title = models.CharField(max_length=50, unique=True, null=False)

    def __str__(self):
        return self.category_title

# 5. 지원금 테이블 (grant)
class Grant(models.Model):
    grant_num = models.AutoField(primary_key=True)
    category_num = models.ForeignKey(Category, on_delete=models.PROTECT, null=False)
    grant_title = models.CharField(max_length=255, null=False)
    grant_description = models.TextField(null=True, blank=True) 
    end_date = models.DateField(null=True, blank=True) 
    link = models.URLField(max_length=2048, null=True, blank=True)

    def __str__(self):
        return self.grant_title
    
# 6. 지원금의 상세 대상 조건 테이블 (grantdetailinfo)
class GrantEligibility(models.Model): # grantdetailinfo -> GrantEligibility로 이름 변경
    grant_num = models.OneToOneField(Grant, on_delete=models.CASCADE, primary_key=True)
    target_age_group = models.CharField(max_length=50, null=True, blank=True)
    min_age = models.IntegerField(null=True, blank=True)
    max_age = models.IntegerField(null=True, blank=True)
    min_income_bracket = models.IntegerField(null=True, blank=True)
    max_income_bracket = models.IntegerField(null=True, blank=True)
    target_region = models.CharField(max_length=100, null=True, blank=True)

    def __str__(self):
        return f"{self.grant_num.grant_title} - 대상 조건"
    
# 7. 신청 수혜기록 테이블 (applicationhistory)
class ApplicationHistory(models.Model):
    application_num = models.AutoField(primary_key=True)
    user_num = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, null=False)
    grant_num = models.ForeignKey(Grant, on_delete=models.CASCADE, null=False)
    application_date = models.DateTimeField(auto_now_add=True, null=False) # '시각 기본값'
    is_beneficiary = models.BooleanField(default=False, null=False) # 'True false 사용자 입력'

    class Meta:
        # '회원 참조, 중복 불가', '지원금 참조, 중복 불가' -> 복합 고유 키
        unique_together = ('user_num', 'grant_num') 

    def __str__(self):
        return f"{self.user_num}의 {self.grant_num} 신청"
    
# 8. 알림 테이블 (notification)
class Notification(models.Model):
    notification_num = models.AutoField(primary_key=True)
    user_num = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, null=False)
    grant_num = models.ForeignKey(Grant, on_delete=models.SET_NULL, null=True, blank=True) # NULL 허용
    notification_type = models.CharField(max_length=20, null=False)
    notification_content = models.TextField(null=False)
    sent_at = models.DateTimeField(auto_now_add=True, null=False) # '시각 기본값'
    
    # ERD에는 있으나 PDF에는 없는 '알림 확인 여부'를 추가하는 것이 좋습니다.
    is_read = models.BooleanField(default=False)
    
    def __str__(self):
        return f"[{self.notification_type}] {self.user_num}에게"
    
# 9. 스크랩 테이블 (scrap)
class Scrap(models.Model):
    scrap_id = models.AutoField(primary_key=True)
    user_num = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, null=False)
    grant_num = models.ForeignKey(Grant, on_delete=models.CASCADE, null=False)
    scrap_date = models.DateTimeField(auto_now_add=True, null=False) # '시각 기본값'

    class Meta:
        # '회원 참조, 중복 불가', '지원금 참조, 중복 불가' -> 복합 고유 키
        unique_together = ('user_num', 'grant_num')

    def __str__(self):
        return f"{self.user_num}의 {self.grant_num} 스크랩"