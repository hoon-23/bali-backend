from datetime import timedelta

from airflow.sdk import DAG
from airflow.providers.docker.operators.docker import DockerOperator

# 매일 09시(KST) = 00시(UTC)에 bali-batch:local 컨테이너를 띄워 RoutineReminderRunner를 실행한다.
# 그날 아직 시작 안 한 SCHEDULED 세션이 있으면 리마인더를 보낸다. 사용자별 시각 지정은 추후 과제.
with DAG(
    dag_id="routine_reminder",
    schedule="0 0 * * *",
    start_date=None,
    catchup=False,
    default_args={
        "retries": 1,
        "retry_delay": timedelta(minutes=5),
    },
) as dag:
    run_routine_reminder = DockerOperator(
        task_id="run_routine_reminder",
        image="bali-batch:local",
        command="routine-reminder",
        force_pull=False,
        auto_remove="success",
        network_mode="bali_default",
        environment={
            "SPRING_DATASOURCE_URL": "jdbc:postgresql://postgres:5432/bali",
        },
    )
