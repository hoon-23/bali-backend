from datetime import timedelta

from airflow.sdk import DAG
from airflow.providers.docker.operators.docker import DockerOperator

# 매일 09시(KST) = 00시(UTC)에 bali-batch:local 컨테이너를 띄워 InactivityAlertRunner를 실행한다.
# 마지막 완료 세션이 7일 이상 지난 ACTIVE 유저에게 이탈 알림을 보낸다.
with DAG(
    dag_id="inactivity_alert",
    schedule="0 0 * * *",
    start_date=None,
    catchup=False,
    default_args={
        "retries": 1,
        "retry_delay": timedelta(minutes=5),
    },
) as dag:
    run_inactivity_alert = DockerOperator(
        task_id="run_inactivity_alert",
        image="bali-batch:local",
        command="inactivity-alert",
        force_pull=False,
        auto_remove="success",
        network_mode="bali_default",
        environment={
            "SPRING_DATASOURCE_URL": "jdbc:postgresql://postgres:5432/bali",
        },
    )
