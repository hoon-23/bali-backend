from datetime import timedelta

from airflow.sdk import DAG
from airflow.providers.docker.operators.docker import DockerOperator

# 매주 월요일, bali-batch:local 컨테이너를 sibling으로 띄워 WeeklyAnalysisRunner를 실행하고,
# 성공하면 이어서 WeeklySummaryPushRunner로 방금 집계된 결과를 요약 알림으로 보낸다.
# exit code 0/1을 DockerOperator가 그대로 태스크 성공/실패로 반영한다.
with DAG(
    dag_id="weekly_analysis",
    schedule="0 0 * * 1",
    start_date=None,
    catchup=False,
    default_args={
        "retries": 1,
        "retry_delay": timedelta(minutes=5),
    },
) as dag:
    run_weekly_analysis = DockerOperator(
        task_id="run_weekly_analysis",
        image="bali-batch:local",
        command="weekly",
        force_pull=False,
        auto_remove="success",
        network_mode="bali_default",
        environment={
            "SPRING_DATASOURCE_URL": "jdbc:postgresql://postgres:5432/bali",
        },
    )

    send_weekly_summary_push = DockerOperator(
        task_id="send_weekly_summary_push",
        image="bali-batch:local",
        command="weekly-summary-push",
        force_pull=False,
        auto_remove="success",
        network_mode="bali_default",
        environment={
            "SPRING_DATASOURCE_URL": "jdbc:postgresql://postgres:5432/bali",
        },
    )

    run_weekly_analysis >> send_weekly_summary_push
