export const HOUSEHOLD_OPTIONS = [
  { code: 'SINGLE', label: '1인 가구' },
  { code: 'NEWLYWED', label: '신혼 부부' },
  { code: 'MULTI_CHILD', label: '다자녀' },
  { code: 'SINGLE_PARENT', label: '한부모' },
];

export const SPECIAL_OPTIONS = [
  { code: 'DISABLED', label: '장애인' },
  { code: 'VETERAN', label: '국가유공자' },
  { code: 'NEWLYWED', label: '신혼' },
  { code: 'MULTI_CHILD', label: '다자녀' },
  { code: 'OTHER', label: '기타' },
];

export function codesFor(options, labels) {
  return (labels || [])
    .map((label) => options.find((option) => option.label === label)?.code)
    .filter(Boolean);
}

export function labelsFor(options, codes) {
  return (codes || [])
    .map((code) => options.find((option) => option.code === code)?.label)
    .filter(Boolean);
}

export function labelText(options, codes) {
  const labels = labelsFor(options, codes);
  return labels.length > 0 ? labels.join(', ') : '선택 없음';
}
