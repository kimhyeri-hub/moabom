from django.contrib import admin
from .models import Category, Grant, GrantEligibility, ApplicationHistory, Notification, Scrap
# Register your models here.

admin.site.register(Category)
admin.site.register(Grant)
admin.site.register(GrantEligibility)
admin.site.register(ApplicationHistory)
admin.site.register(Notification)
admin.site.register(Scrap)