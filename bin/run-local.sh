#!/usr/bin/env zsh
set -euo pipefail

project_dir="${0:A:h:h}"
env_file="$project_dir/.local.env"

if [[ ! -f "$env_file" ]]; then
  echo "缺少 $env_file，请先配置本地数据库账号和 BI_DATASOURCE_MASTER_KEY。" >&2
  exit 1
fi

set -a
source "$env_file"
set +a

export BI_META_DB_URL="${BI_META_DB_URL:-jdbc:mysql://127.0.0.1:3306/sk_bi_meta?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=GMT%2B8}"
exec java -jar "$project_dir/ruoyi-admin/target/ruoyi-admin.jar"
