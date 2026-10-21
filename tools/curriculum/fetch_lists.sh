#!/bin/sh
# Download the three public reference lists that tag_lexicon.py matches against.
#
#   tools/curriculum/fetch_lists.sh <empty directory outside the repository>
#
# The lists are never committed (terms: see README.txt). Treat them as untrusted data: keep them in their own
# directory and read them only with tag_lexicon.py (python3 -I).
set -eu
dir=${1:?usage: fetch_lists.sh <directory>}
mkdir -p "$dir"

# 1. standard curriculum word list in three bands, as published by the Korean Language Teaching and Learning Center
#    (data.go.kr dataset 15152177; the attachment id may change when the dataset is re-registered)
curl -fsSL -A Mozilla/5.0 -e https://www.data.go.kr/data/15152177/fileData.do \
	-o "$dir/nikl-learner-vocab-2017.csv" \
	"https://www.data.go.kr/cmm/cmm/fileDownload.do?atchFileId=FILE_000000003526766&fileDetailSn=1&insertDataPrcus=N"

# 2. 한국어 학습용 어휘 목록 (2003), text file
curl -fsSL -o "$dir/nikl-learning-vocab-2003.txt" \
	"https://www.korean.go.kr/common/download.do?file_path=etcData&c_file_name=b73a8438-4713-4436-8481-ec26fd0dce2a_0.txt&o_file_name=%ED%95%9C%EA%B5%AD%EC%96%B4%20%ED%95%99%EC%8A%B5%EC%9A%A9%20%EC%96%B4%ED%9C%98%20%EB%AA%A9%EB%A1%9D.txt"

# 3. 2023 국어 기초 어휘 선정 및 어휘 등급화 목록 (40,000 words, 5 grades, origin and hanja)
curl -fsSL -o "$dir/nikl-basic-vocab-2023.xlsx" \
	"https://www.korean.go.kr/common/download.do?file_path=reportData&c_file_name=1d6d75b9-45cb-49d4-989f-1483c332573a.xlsx&o_file_name=%EA%B5%AD%EC%96%B4%20%EA%B8%B0%EC%B4%88%20%EC%96%B4%ED%9C%98%20%EC%84%A0%EC%A0%95%20%EB%B0%8F%20%EC%96%B4%ED%9C%98%20%EB%93%B1%EA%B8%89%ED%99%94%20%EB%AA%A9%EB%A1%9D%20%EC%A0%84%EC%B2%B4.xlsx"

sha256sum "$dir"/nikl-*
