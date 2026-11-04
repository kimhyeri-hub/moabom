import json

from django.test import Client, TestCase, override_settings

from accounts.models import User

SECRET = "django-test-secret-not-for-deployment"


@override_settings(MOABOM_AUTH_SECRET=SECRET)
class AuthApiTests(TestCase):
    def setUp(self):
        self.client = Client()

    def payload(self, **overrides):
        body = {
            "name": "김모아",
            "loginId": "moa01",
            "password": "Moabom-pass-1",
            "dateOfBirth": "20011104",
            "email": "moa01@example.com",
            "region": "예시특별시",
            "incomeBracket": 3,
        }
        body.update(overrides)
        return body

    def post(self, path, body):
        return self.client.post(path, data=json.dumps(body), content_type="application/json")

    def test_signup_login_and_password_hash(self):
        created = self.post("/api/auth/signup/", self.payload())
        self.assertEqual(created.status_code, 201)
        user = User.objects.get(login_id="moa01")
        self.assertNotEqual(user.password, "Moabom-pass-1")
        self.assertTrue(user.password.startswith("pbkdf2_"))
        self.assertTrue(user.check_password("Moabom-pass-1"))
        self.assertEqual(created.json()["user"]["id"], str(user.user_num))
        self.assertEqual(user.userdetailinfo.region, "예시특별시")
        self.assertEqual(user.userdetailinfo.income_bracket, 3)

        duplicate = self.post("/api/auth/signup/", self.payload(email="other@example.com"))
        self.assertEqual(duplicate.status_code, 409)

        missing = self.post("/api/auth/signup/", self.payload(loginId="moa02", email="moa02@example.com", name=""))
        self.assertEqual(missing.status_code, 400)

        bad_login = self.post("/api/auth/login/", {"loginId": "moa01", "password": "wrong-pass-1"})
        self.assertEqual(bad_login.status_code, 401)

        good_login = self.post("/api/auth/login/", {"loginId": "moa01", "password": "Moabom-pass-1"})
        self.assertEqual(good_login.status_code, 200)
        token = good_login.json()["token"]

        me = self.client.get("/api/auth/me/", HTTP_AUTHORIZATION=f"Bearer {token}")
        self.assertEqual(me.status_code, 200)
        self.assertEqual(me.json()["user"]["loginId"], "moa01")

        anonymous = self.client.get("/api/auth/me/")
        self.assertEqual(anonymous.status_code, 401)

        logged_out = self.client.post("/api/auth/logout/", HTTP_AUTHORIZATION=f"Bearer {token}")
        self.assertEqual(logged_out.status_code, 200)

    def test_missing_secret_does_not_issue_a_token(self):
        with override_settings(MOABOM_AUTH_SECRET=""):
            response = self.post("/api/auth/signup/", self.payload(loginId="moa03", email="moa03@example.com"))
        self.assertEqual(response.status_code, 500)
        self.assertFalse(response.content.decode("utf-8").__contains__("token"))
