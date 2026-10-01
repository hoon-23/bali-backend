from datetime import timedelta

from airflow.sdk import DAG
from airflow.providers.docker.operators.docker import DockerOperator

# 매일 00시 05분(KST) = 15시 05분(UTC)에 bali-batch:local 컨테이너를 띄워 AbandonStaleSessionsRunner를 실행한다.
# 자정(KST)을 넘긴 IN_PROGRESS 세션을 ABANDONED(중단)로 확정한다. 멱등이라 재시도해도 안전하다.
with DAG(
    dag_id="abandon_stale_sessions",
    schedule="5 15 * * *",
    start_date=None,
    catchup=False,
    default_args={
        "retries": 1,
        "retry_delay": timedelta(minutes=5),
    },
) as dag:
    run_abandon_stale_sessions = DockerOperator(
        task_id="run_abandon_stale_sessions",
        image="bali-batch:local",
        command="abandon-stale-sessions",
        force_pull=False,
        auto_remove="success",
        network_mode="bali_default",
        environment={
            "SPRING_DATASOURCE_URL": "jdbc:postgresql://postgres:5432/bali",
        },
    )
