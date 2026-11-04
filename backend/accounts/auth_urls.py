from django.urls import path

from .auth_views import login_api, login_id_api, logout_api, me_api, signup_api

urlpatterns = [
    path("signup/", signup_api),
    path("login/", login_api),
    path("logout/", logout_api),
    path("me/", me_api),
    path("login-id/", login_id_api),
]
