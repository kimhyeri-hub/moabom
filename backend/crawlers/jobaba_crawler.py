import requests
from bs4 import BeautifulSoup
import re 
from datetime import date 

# 이 함수는 크롤링한 데이터를 딕셔너리 형태로 반환합니다.
def get_jobaba_grant_data(url):
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36"
    }

    try:
        response = requests.get(url, headers=headers)
        response.raise_for_status()

        soup = BeautifulSoup(response.text, 'html.parser')

        # 1. 지원금 제목 추출 (최종 확정된 선택자)
        title_element = soup.select_one('div.about_define h2') # 스크린샷 기반
        title = title_element.get_text(strip=True) if title_element else "제목을 찾을 수 없음"
        # 만약 "2025년 청년기본소득 4분기 신청"이 제목이라면, 해당 부분을 다시 찾아야 합니다.
        # 예: title_element_main = soup.select_one('div.section_sub_menu h2')
        # title = title_element_main.get_text(strip=True) if title_element_main else title

        # 2. 지원금 설명 추출
        description_elements = soup.select('div.about_info dl > dt, div.about_info dl > dd')
        description_parts = []
        
        # div.about_define의 p 태그 내용 추가 (만약 있다면)
        intro_p_element_define = soup.select_one('div.about_define p')
        if intro_p_element_define:
            description_parts.append(intro_p_element_define.get_text(strip=True))

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
        deadline_element = soup.select_one('div#bsnsDetail div.checkpoint_wrap dl.bsns_info dd')
        deadline_raw_text = deadline_element.get_text(strip=True) if deadline_element else "마감일을 찾을 수 없음"
        
        date_pattern = re.compile(r'(\d{4}\.\s*\d{1,2}\.\s*\d{1,2}\.)')
        dates = date_pattern.findall(deadline_raw_text)
        
        application_deadline = None
        if len(dates) > 1: # 시작일과 종료일이 모두 있을 경우
            end_date_str = dates[1].replace(' ', '')
            try:
                application_deadline = date.fromisoformat(end_date_str.replace('.', '-').rstrip('-')) 
            except ValueError:
                pass
        elif len(dates) == 1: # 종료일만 있을 경우 (시작일과 동일)
             end_date_str = dates[0].replace(' ', '')
             try:
                 application_deadline = date.fromisoformat(end_date_str.replace('.', '-').rstrip('-'))
             except ValueError:
                 pass


        # 4. 신청 링크 추출
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

        # 5. 크롤링된 데이터를 딕셔너리 형태로 반환
        return {
            'grant_title': title,
            'grant_description': description,
            'application_deadline': application_deadline,
            'application_link': application_link,
            # 아래 필드들은 GrantEligibility 모델에 들어갈 정보입니다.
            # 페이지에서 직접 추출해야 하지만, 일단은 기본값으로 설정합니다.
            'target_age_group': 'Youth', # 예: 'Youth', 'Adult', 'Senior'
            'min_age': 19, # 예: 19세 이상
            'max_age': 34, # 예: 34세 이하
            'min_income_bracket': None, # 소득 정보가 페이지에 있다면 추출해야 합니다.
            'max_income_bracket': None, # None은 해당 없음
            'target_region': 'Gyeonggi-do' # 예: 'Gyeonggi-do', 'Seoul'
        }

    except requests.exceptions.RequestException as e:
        print(f"HTTP 요청 오류: {e}")
        return None
    except Exception as e:
        print(f"오류 발생: {e}")
        return None

# --- 스크립트 직접 실행 시 테스트 코드 ---
if __name__ == "__main__":
    jobaba_url = "https://apply.jobaba.net/special/gibon/bsnsInfo.do"
    crawled_data = get_jobaba_grant_data(jobaba_url)

    if crawled_data:
        print("\n--- 크롤링된 딕셔너리 데이터 ---")
        for key, value in crawled_data.items():
            print(f"{key}: {value}")
    else:
        print("데이터 크롤링에 실패했습니다.")