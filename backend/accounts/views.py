from django.shortcuts import render, redirect
from django.contrib.auth.forms import UserCreationForm # 장고의 내장 회원가입 폼

def signup(request):
    if request.method == 'POST': # 사용자가 회원가입 정보를 입력하고 버튼을 눌렀을 때
        form = UserCreationForm(request.POST)
        if form.is_valid(): # 입력된 정보가 유효하다면
            form.save() # 사용자를 생성하고 저장
            return redirect('login') # 로그인 페이지로 이동 (URL 이름이 'login'이라고 가정)
    else: # 그냥 페이지에 처음 접속했을 때 (GET 요청)
        form = UserCreationForm() # 비어있는 회원가입 폼을 생성
    
    return render(request, 'accounts/signup.html', {'form': form})