import requests
from bs4 import BeautifulSoup
import re 
from datetime import date # date 객체 사용을 위해 추가

# 1. 크롤링할 URL
url = "https://apply.jobaba.net/special/gibon/bsnsInfo.do"

# 2. (선택 사항) 크롤러가 봇이 아닌 브라우저인 것처럼 위장
headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36"
}

try:
    # 3. URL에 접속하여 HTML 정보 가져오기
    response = requests.get(url, headers=headers)
    response.raise_for_status() # 오류가 발생하면 예외 발생

    # 4. HTML을 파싱(분석)하기 좋은 형태로 변환
    soup = BeautifulSoup(response.text, 'html.parser')

    # --- F12로 찾은 CSS 선택자로 정보 추출 ---
    
    # 1. 지원금 제목 추출 (수정 완료)
    # div.top_title_wrap 안에 있는 h2 태그를 찾습니다.
    title_element = soup.select_one('div.about_define h2')
    title = title_element.get_text(strip=True) if title_element else "제목을 찾을 수 없음"

    # 2. 지원금 설명 추출 ('about_info' div 안의 dl 목록들을 통합)
    description_elements = soup.select('div.about_info dl > dt, div.about_info dl > dd')
    description_parts = []
    
    intro_p_element = soup.select_one('div.about_info h2.gyeonggiIF_about_title + p')
    if intro_p_element:
        description_parts.append(intro_p_element.get_text(strip=True))

    current_dt = ""
    for element in description_elements:
        if element.name == 'dt':
            current_dt = element.get_text(strip=True)
            description_parts.append(f"{current_dt}:")
        elif element.name == 'dd':
            dd_text = element.get_text(strip=True)
            table_element = element.select_one('table')
            if table_element:
                table_text = ""
                for row in table_element.find_all('tr'):
                    row_data = [cell.get_text(strip=True) for cell in row.find_all(['td', 'th'])]
                    table_text += " | ".join(row_data) + "\n"
                description_parts.append(table_text.strip())
            else:
                description_parts.append(dd_text)
            current_dt = "" 
            
    description = "\n".join(description_parts)
    if not description:
        description = "설명을 찾을 수 없음 (about_info 구조 확인 필요)"


    # 3. 신청 기간(마감일) 추출 
    # div#bsnsDetail div.checkpoint_wrap dl.bsns_info dd 태그
    deadline_element = soup.select_one('div#bsnsDetail div.checkpoint_wrap dl.bsns_info dd')
    deadline_raw_text = deadline_element.get_text(strip=True) if deadline_element else "마감일을 찾을 수 없음"
    
    date_pattern = re.compile(r'(\d{4}\.\s*\d{1,2}\.\s*\d{1,2}\.)')
    dates = date_pattern.findall(deadline_raw_text)
    
    start_date_str = dates[0].replace(' ', '') if len(dates) > 0 else None
    end_date_str = dates[1].replace(' ', '') if len(dates) > 1 else None

    application_deadline = None
    if end_date_str:
        try:
            application_deadline = date.fromisoformat(end_date_str.replace('.', '-').rstrip('-')) 
        except ValueError:
            pass 

    # 4. 신청 링크 추출 (수정 완료)
    # div.register 안에 있는 'btn_big'과 'btn_black' 클래스를 가진 a 태그
    link_element = soup.select_one('div.register a.btn_big.btn_black')
    
    raw_js_link = link_element['href'] if link_element and 'href' in link_element.attrs else "링크를 찾을 수 없음"
    
    match = re.search(r"'(/\S+\.do)'", raw_js_link)
    application_link = "URL 추출 실패"
    if match:
        extracted_url_path = match.group(1)
        base_url_match = re.match(r"(https?://[^/]+)", url)
        if base_url_match:
            base_url_domain = base_url_match.group(1)
            application_link = base_url_domain + extracted_url_path
        else:
            application_link = extracted_url_path
    else:
        application_link = raw_js_link


    # ---------------------------------------------------

    # 5. 결과 출력
    print(f"지원금 제목: {title}")
    print(f"지원금 설명: {description[:500]}...")
    print(f"신청 마감일 (원본): {deadline_raw_text}")
    print(f"신청 마감일 (파싱): {application_deadline}")
    print(f"신청 링크: {application_link}")

except requests.exceptions.RequestException as e:
    print(f"HTTP 요청 오류: {e}")
except Exception as e:
    print(f"오류 발생: {e}")