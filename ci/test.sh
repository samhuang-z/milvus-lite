#!/bin/bash
export PIP_TRUSTED_HOST="nexus-ci.zilliz.cc"
export PIP_INDEX_URL="https://nexus-ci.zilliz.cc/repository/pypi-all/simple"
export PIP_INDEX="https://nexus-ci.zilliz.cc/repository/pypi-all/pypi"
export PIP_FIND_LINKS="https://nexus-ci.zilliz.cc/repository/pypi-all/pypi"
python3 -m pip install --no-cache-dir -r requirements.txt --timeout 300 --retries 6

pytest -s  -v  --tags  L0  --enable_milvus_local_api  lite-e2e.db

