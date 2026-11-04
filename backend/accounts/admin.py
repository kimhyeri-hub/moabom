from django.contrib import admin

from .models import User, UserDetailInfo, UserType


@admin.register(User)
class MoabomUserAdmin(admin.ModelAdmin):
    list_display = ("login_id", "name", "email", "is_active")
    search_fields = ("login_id", "name", "email")
    readonly_fields = ("password",)


admin.site.register(UserType)
admin.site.register(UserDetailInfo)
