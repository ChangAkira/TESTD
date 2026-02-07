import os
import random
from pathlib import Path
from urllib.parse import quote

from flask import Flask, jsonify, request
import mysql.connector
from mysql.connector import errorcode


app = Flask(__name__)

BASE_DIR = Path(__file__).resolve().parent
AVATAR_DIR = BASE_DIR / "static" / "avatars"

DB_CONFIG = {
    "host": os.getenv("DB_HOST", "localhost"),
    "user": os.getenv("DB_USER", "root"),
    "password": os.getenv("DB_PASSWORD", "111111"),
    "database": os.getenv("DB_NAME", "testd"),
    "autocommit": False,
}


def get_connection():
    return mysql.connector.connect(**DB_CONFIG)


def list_default_avatars():
    if not AVATAR_DIR.exists():
        return []
    return sorted(
        [p.name for p in AVATAR_DIR.iterdir() if p.is_file() and p.name.startswith("default")]
    )


def pick_default_avatar():
    candidates = list_default_avatars()
    if not candidates:
        return None
    return random.choice(candidates)


def build_avatar_url(filename):
    if not filename:
        return None
    return f"/static/avatars/{quote(filename)}"


def is_allowed_image(filename, content_type):
    if not filename:
        return False
    lower = filename.lower()
    if not (
        lower.endswith(".jpg")
        or lower.endswith(".jpeg")
        or lower.endswith(".png")
        or lower.endswith(".gif")
        or lower.endswith(".webp")
        or lower.endswith(".bmp")
        or lower.endswith(".heic")
        or lower.endswith(".heif")
    ):
        return False
    if content_type and content_type not in (
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/gif",
        "image/webp",
        "image/bmp",
        "image/heic",
        "image/heif",
    ):
        return False
    return True


def is_user_avatar(filename, student_id):
    if not filename:
        return False
    lower = filename.lower()
    return lower in (
        f"{student_id.lower()}.jpg",
        f"{student_id.lower()}.jpeg",
        f"{student_id.lower()}.png",
        f"{student_id.lower()}.gif",
        f"{student_id.lower()}.webp",
        f"{student_id.lower()}.bmp",
        f"{student_id.lower()}.heic",
        f"{student_id.lower()}.heif",
    )


def delete_user_avatar_if_needed(student_id, current_filename, new_filename=None):
    if not is_user_avatar(current_filename, student_id):
        return
    if new_filename and current_filename == new_filename:
        return
    path = AVATAR_DIR / current_filename
    if path.exists():
        path.unlink()


def ensure_tasks_table(conn):
    create_sql = """
    CREATE TABLE IF NOT EXISTS TASKS (
        id INT AUTO_INCREMENT PRIMARY KEY,
        publisher_id VARCHAR(64),
        acceptor_id VARCHAR(64),
        title VARCHAR(128),
        description TEXT,
        start_location VARCHAR(128),
        target_location VARCHAR(128),
        start_lat DOUBLE,
        start_lng DOUBLE,
        target_lat DOUBLE,
        target_lng DOUBLE,
        reward DECIMAL(10,2),
        status VARCHAR(32),
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """
    with conn.cursor() as cursor:
        cursor.execute(create_sql)
    conn.commit()


def ensure_acceptor_column(conn):
    with conn.cursor() as cursor:
        cursor.execute("SHOW COLUMNS FROM TASKS LIKE 'acceptor_id'")
        if cursor.fetchone() is None:
            cursor.execute("ALTER TABLE TASKS ADD COLUMN acceptor_id VARCHAR(64)")
    conn.commit()


def ensure_task_location_columns(conn):
    columns = {
        "start_lat": "DOUBLE",
        "start_lng": "DOUBLE",
        "target_lat": "DOUBLE",
        "target_lng": "DOUBLE",
    }
    with conn.cursor() as cursor:
        for column, col_type in columns.items():
            cursor.execute("SHOW COLUMNS FROM TASKS LIKE %s", (column,))
            if cursor.fetchone() is None:
                cursor.execute(f"ALTER TABLE TASKS ADD COLUMN {column} {col_type}")
    conn.commit()


def ensure_table_exists(conn):
    create_sql = """
    CREATE TABLE IF NOT EXISTS USERINFOS (
        student_id VARCHAR(64) PRIMARY KEY,
        name VARCHAR(128),
        username VARCHAR(128),
        avatar_filename VARCHAR(255),
        balance DECIMAL(10,2) DEFAULT 0
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """
    with conn.cursor() as cursor:
        cursor.execute(create_sql)
    conn.commit()


def ensure_avatar_column(conn):
    with conn.cursor() as cursor:
        cursor.execute("SHOW COLUMNS FROM USERINFOS LIKE 'avatar_filename'")
        if cursor.fetchone() is None:
            cursor.execute("ALTER TABLE USERINFOS ADD COLUMN avatar_filename VARCHAR(255)")
    conn.commit()


def ensure_balance_column(conn):
    with conn.cursor() as cursor:
        cursor.execute("SHOW COLUMNS FROM USERINFOS LIKE 'balance'")
        if cursor.fetchone() is None:
            cursor.execute("ALTER TABLE USERINFOS ADD COLUMN balance DECIMAL(10,2) DEFAULT 0")
    conn.commit()


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok"}), 200


@app.route("/users", methods=["POST"])
def create_user():
    data = request.get_json(silent=True) or {}
    student_id = (data.get("student_id") or "").strip()
    name = (data.get("name") or "").strip()
    username = (data.get("username") or student_id).strip()

    if not student_id or not name:
        return jsonify({"error": "student_id and name are required"}), 400

    conn = None
    try:
        conn = get_connection()
        ensure_table_exists(conn)
        ensure_avatar_column(conn)
        ensure_balance_column(conn)
        avatar_filename = pick_default_avatar()
        with conn.cursor() as cursor:
            insert_sql = """
            INSERT INTO USERINFOS (student_id, name, username, avatar_filename, balance)
            VALUES (%s, %s, %s, %s, %s)
            """
            cursor.execute(insert_sql, (student_id, name, username, avatar_filename, 0))
        conn.commit()
        return jsonify({"status": "created"}), 201
    except mysql.connector.Error as exc:
        if exc.errno == errorcode.ER_DUP_ENTRY:
            return jsonify({"status": "exists"}), 409
        return jsonify({"error": str(exc)}), 500
    finally:
        if conn is not None:
            conn.close()


@app.route("/users/<student_id>", methods=["GET"])
def get_user(student_id):
    student_id = (student_id or "").strip()
    if not student_id:
        return jsonify({"error": "student_id is required"}), 400

    conn = None
    try:
        conn = get_connection()
        ensure_table_exists(conn)
        ensure_avatar_column(conn)
        ensure_balance_column(conn)
        with conn.cursor(dictionary=True) as cursor:
            cursor.execute(
                "SELECT student_id, name, username, avatar_filename, balance FROM USERINFOS WHERE student_id = %s",
                (student_id,),
            )
            row = cursor.fetchone()
        if not row:
            return jsonify({"error": "not found"}), 404
        if not row.get("avatar_filename"):
            avatar_filename = pick_default_avatar()
            if avatar_filename:
                with conn.cursor() as cursor:
                    cursor.execute(
                        "UPDATE USERINFOS SET avatar_filename = %s WHERE student_id = %s",
                        (avatar_filename, student_id),
                    )
                conn.commit()
                row["avatar_filename"] = avatar_filename
        row["avatar_url"] = build_avatar_url(row.get("avatar_filename"))
        return jsonify(row), 200
    except mysql.connector.Error as exc:
        return jsonify({"error": str(exc)}), 500
    finally:
        if conn is not None:
            conn.close()


@app.route("/users/<student_id>", methods=["PUT"])
def update_username(student_id):
    student_id = (student_id or "").strip()
    data = request.get_json(silent=True) or {}
    username = (data.get("username") or "").strip()

    if not student_id or not username:
        return jsonify({"error": "student_id and username are required"}), 400

    conn = None
    try:
        conn = get_connection()
        ensure_table_exists(conn)
        ensure_avatar_column(conn)
        ensure_balance_column(conn)
        with conn.cursor() as cursor:
            cursor.execute(
                "UPDATE USERINFOS SET username = %s WHERE student_id = %s",
                (username, student_id),
            )
            if cursor.rowcount == 0:
                return jsonify({"error": "not found"}), 404
        conn.commit()
        with conn.cursor(dictionary=True) as cursor:
            cursor.execute(
                "SELECT student_id, name, username, avatar_filename, balance FROM USERINFOS WHERE student_id = %s",
                (student_id,),
            )
            row = cursor.fetchone()
        if row:
            row["avatar_url"] = build_avatar_url(row.get("avatar_filename"))
        return jsonify(row), 200
    except mysql.connector.Error as exc:
        return jsonify({"error": str(exc)}), 500
    finally:
        if conn is not None:
            conn.close()


@app.route("/users/<student_id>/avatar", methods=["GET"])
def get_avatar(student_id):
    student_id = (student_id or "").strip()
    if not student_id:
        return jsonify({"error": "student_id is required"}), 400

    conn = None
    try:
        conn = get_connection()
        ensure_table_exists(conn)
        ensure_avatar_column(conn)
        with conn.cursor(dictionary=True) as cursor:
            cursor.execute(
                "SELECT avatar_filename FROM USERINFOS WHERE student_id = %s",
                (student_id,),
            )
            row = cursor.fetchone()
        if not row:
            return jsonify({"error": "not found"}), 404
        avatar_filename = row.get("avatar_filename") or pick_default_avatar()
        if avatar_filename and avatar_filename != row.get("avatar_filename"):
            with conn.cursor() as cursor:
                cursor.execute(
                    "UPDATE USERINFOS SET avatar_filename = %s WHERE student_id = %s",
                    (avatar_filename, student_id),
                )
            conn.commit()
        return (
            jsonify(
                {
                    "avatar_filename": avatar_filename,
                    "avatar_url": build_avatar_url(avatar_filename),
                }
            ),
            200,
        )
    except mysql.connector.Error as exc:
        return jsonify({"error": str(exc)}), 500
    finally:
        if conn is not None:
            conn.close()


@app.route("/users/<student_id>/avatar/default", methods=["PUT"])
def set_default_avatar(student_id):
    student_id = (student_id or "").strip()
    data = request.get_json(silent=True) or {}
    avatar_filename = (data.get("avatar_filename") or "").strip()

    if not student_id or not avatar_filename:
        return jsonify({"error": "student_id and avatar_filename are required"}), 400

    if avatar_filename not in list_default_avatars():
        return jsonify({"error": "invalid default avatar"}), 400

    conn = None
    try:
        conn = get_connection()
        ensure_table_exists(conn)
        ensure_avatar_column(conn)
        with conn.cursor() as cursor:
            cursor.execute(
                "SELECT avatar_filename FROM USERINFOS WHERE student_id = %s",
                (student_id,),
            )
            row = cursor.fetchone()
            current_filename = row[0] if row else None
            cursor.execute(
                "UPDATE USERINFOS SET avatar_filename = %s WHERE student_id = %s",
                (avatar_filename, student_id),
            )
            if cursor.rowcount == 0:
                return jsonify({"error": "not found"}), 404
        conn.commit()
        delete_user_avatar_if_needed(student_id, current_filename)
        return (
            jsonify(
                {
                    "avatar_filename": avatar_filename,
                    "avatar_url": build_avatar_url(avatar_filename),
                }
            ),
            200,
        )
    except mysql.connector.Error as exc:
        return jsonify({"error": str(exc)}), 500
    finally:
        if conn is not None:
            conn.close()


@app.route("/users/<student_id>/avatar", methods=["POST"])
def upload_avatar(student_id):
    student_id = (student_id or "").strip()
    if not student_id:
        return jsonify({"error": "student_id is required"}), 400

    if "avatar" not in request.files:
        return jsonify({"error": "avatar file is required"}), 400

    avatar_file = request.files["avatar"]
    if not is_allowed_image(avatar_file.filename, avatar_file.content_type):
        return jsonify({"error": "only png/jpg/gif/webp/bmp/heic/heif are allowed"}), 400

    AVATAR_DIR.mkdir(parents=True, exist_ok=True)
    ext = os.path.splitext(avatar_file.filename)[1].lower()
    if ext == ".jpeg":
        ext = ".jpg"
    filename = f"{student_id}{ext}"
    save_path = AVATAR_DIR / filename
    avatar_file.save(save_path)

    conn = None
    try:
        conn = get_connection()
        ensure_table_exists(conn)
        ensure_avatar_column(conn)
        with conn.cursor() as cursor:
            cursor.execute(
                "SELECT avatar_filename FROM USERINFOS WHERE student_id = %s",
                (student_id,),
            )
            row = cursor.fetchone()
            current_filename = row[0] if row else None
            cursor.execute(
                "UPDATE USERINFOS SET avatar_filename = %s WHERE student_id = %s",
                (filename, student_id),
            )
            if row is None:
                return jsonify({"error": "not found"}), 404
            if cursor.rowcount == 0:
                pass
        conn.commit()
        delete_user_avatar_if_needed(student_id, current_filename, filename)
        return (
            jsonify(
                {
                    "avatar_filename": filename,
                    "avatar_url": build_avatar_url(filename),
                }
            ),
            200,
        )
    except mysql.connector.Error as exc:
        return jsonify({"error": str(exc)}), 500
    finally:
        if conn is not None:
            conn.close()


@app.route("/tasks", methods=["POST"])
def create_task():
    data = request.get_json(silent=True) or {}
    publisher_id = (data.get("publisher_id") or "").strip()
    title = (data.get("title") or "").strip()
    description = (data.get("description") or "").strip()
    start_location = (data.get("start_location") or "").strip()
    target_location = (data.get("target_location") or "").strip()
    start_lat = data.get("start_lat")
    start_lng = data.get("start_lng")
    target_lat = data.get("target_lat")
    target_lng = data.get("target_lng")
    reward = data.get("reward")

    if not publisher_id or not title or reward is None:
        return jsonify({"error": "publisher_id, title and reward are required"}), 400
    if start_lat is None or start_lng is None or target_lat is None or target_lng is None:
        return jsonify({"error": "start_lat, start_lng, target_lat, target_lng are required"}), 400

    try:
        reward_value = float(reward)
    except (TypeError, ValueError):
        return jsonify({"error": "reward must be a number"}), 400
    try:
        start_lat_value = float(start_lat)
        start_lng_value = float(start_lng)
        target_lat_value = float(target_lat)
        target_lng_value = float(target_lng)
    except (TypeError, ValueError):
        return jsonify({"error": "lat/lng must be numbers"}), 400

    conn = None
    try:
        conn = get_connection()
        ensure_tasks_table(conn)
        ensure_acceptor_column(conn)
        ensure_task_location_columns(conn)
        with conn.cursor() as cursor:
            insert_sql = """
            INSERT INTO TASKS (
                publisher_id,
                acceptor_id,
                title,
                description,
                start_location,
                target_location,
                start_lat,
                start_lng,
                target_lat,
                target_lng,
                reward,
                status
            )
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            """
            cursor.execute(
                insert_sql,
                (
                    publisher_id,
                    None,
                    title,
                    description,
                    start_location,
                    target_location,
                    start_lat_value,
                    start_lng_value,
                    target_lat_value,
                    target_lng_value,
                    reward_value,
                    "等待接单",
                ),
            )
            task_id = cursor.lastrowid
        conn.commit()
        return jsonify({"id": task_id, "status": "created"}), 201
    except mysql.connector.Error as exc:
        return jsonify({"error": str(exc)}), 500
    finally:
        if conn is not None:
            conn.close()


@app.route("/tasks", methods=["GET"])
def list_tasks():
    publisher_id = (request.args.get("publisher_id") or "").strip()
    acceptor_id = (request.args.get("acceptor_id") or "").strip()
    if not publisher_id and not acceptor_id:
        return jsonify({"error": "publisher_id or acceptor_id is required"}), 400

    conn = None
    try:
        conn = get_connection()
        ensure_tasks_table(conn)
        ensure_acceptor_column(conn)
        ensure_task_location_columns(conn)
        with conn.cursor(dictionary=True) as cursor:
            if publisher_id:
                cursor.execute(
                    """
                    SELECT id, publisher_id, acceptor_id, title, description, start_location, target_location,
                           start_lat, start_lng, target_lat, target_lng, reward, status, created_at
                    FROM TASKS
                    WHERE publisher_id = %s
                    ORDER BY created_at DESC, id DESC
                    """,
                    (publisher_id,),
                )
            else:
                cursor.execute(
                    """
                    SELECT id, publisher_id, acceptor_id, title, description, start_location, target_location,
                           start_lat, start_lng, target_lat, target_lng, reward, status, created_at
                    FROM TASKS
                    WHERE acceptor_id = %s
                    ORDER BY created_at DESC, id DESC
                    """,
                    (acceptor_id,),
                )
            rows = cursor.fetchall()
        return jsonify({"tasks": rows}), 200
    except mysql.connector.Error as exc:
        return jsonify({"error": str(exc)}), 500
    finally:
        if conn is not None:
            conn.close()


@app.route("/tasks/<int:task_id>", methods=["GET"])
def get_task_detail(task_id):
    conn = None
    try:
        conn = get_connection()
        ensure_tasks_table(conn)
        ensure_acceptor_column(conn)
        ensure_task_location_columns(conn)
        ensure_table_exists(conn)
        ensure_avatar_column(conn)

        with conn.cursor(dictionary=True) as cursor:
            cursor.execute(
                """
                SELECT id, publisher_id, acceptor_id, title, description, start_location, target_location,
                       start_lat, start_lng, target_lat, target_lng, reward, status, created_at
                FROM TASKS
                WHERE id = %s
                """,
                (task_id,),
            )
            task = cursor.fetchone()

        if not task:
            return jsonify({"error": "task not found"}), 404

        def get_user_brief(student_id):
            if not student_id:
                return None
            with conn.cursor(dictionary=True) as cursor:
                cursor.execute(
                    "SELECT student_id, username, avatar_filename FROM USERINFOS WHERE student_id = %s",
                    (student_id,),
                )
                row = cursor.fetchone()
            if not row:
                return None
            return {
                "student_id": row.get("student_id"),
                "username": row.get("username") or row.get("student_id"),
                "avatar_url": build_avatar_url(row.get("avatar_filename")),
            }

        publisher = get_user_brief(task.get("publisher_id"))
        acceptor = get_user_brief(task.get("acceptor_id"))

        created_at = task.get("created_at")
        if created_at:
            try:
                created_at = created_at.strftime("%Y-%m-%d %H:%M")
            except AttributeError:
                created_at = str(created_at)

        payload = {
            "id": task.get("id"),
            "publisher_id": task.get("publisher_id"),
            "acceptor_id": task.get("acceptor_id"),
            "title": task.get("title"),
            "description": task.get("description"),
            "start_location": task.get("start_location"),
            "target_location": task.get("target_location"),
            "start_lat": task.get("start_lat"),
            "start_lng": task.get("start_lng"),
            "target_lat": task.get("target_lat"),
            "target_lng": task.get("target_lng"),
            "reward": task.get("reward"),
            "status": task.get("status"),
            "created_at": created_at,
            "publisher_username": publisher.get("username") if publisher else None,
            "publisher_avatar_url": publisher.get("avatar_url") if publisher else None,
            "acceptor_username": acceptor.get("username") if acceptor else None,
            "acceptor_avatar_url": acceptor.get("avatar_url") if acceptor else None,
        }
        return jsonify(payload), 200
    except mysql.connector.Error as exc:
        return jsonify({"error": str(exc)}), 500
    finally:
        if conn is not None:
            conn.close()


@app.route("/tasks/<int:task_id>/status", methods=["PUT"])
def update_task_status(task_id):
    data = request.get_json(silent=True) or {}
    new_status = (data.get("status") or "").strip()
    if not new_status:
        return jsonify({"error": "status is required"}), 400

    conn = None
    try:
        conn = get_connection()
        ensure_tasks_table(conn)
        ensure_acceptor_column(conn)
        ensure_balance_column(conn)
        with conn.cursor(dictionary=True) as cursor:
            cursor.execute(
                "SELECT id, publisher_id, acceptor_id, reward, status FROM TASKS WHERE id = %s",
                (task_id,),
            )
            task = cursor.fetchone()
        if not task:
            return jsonify({"error": "task not found"}), 404

        if task["status"] == "待支付" and new_status == "已完成":
            with conn.cursor(dictionary=True) as cursor:
                cursor.execute(
                    "SELECT balance FROM USERINFOS WHERE student_id = %s",
                    (task["publisher_id"],),
                )
                user = cursor.fetchone()
            if not user:
                return jsonify({"error": "user not found"}), 404
            if float(user["balance"]) < float(task["reward"]):
                return jsonify({"error": "insufficient balance"}), 400
            with conn.cursor() as cursor:
                cursor.execute(
                    "UPDATE USERINFOS SET balance = balance - %s WHERE student_id = %s",
                    (task["reward"], task["publisher_id"]),
                )

        acceptor_id = (data.get("acceptor_id") or "").strip()
        if acceptor_id:
            with conn.cursor() as cursor:
                cursor.execute(
                    "UPDATE TASKS SET status = %s, acceptor_id = %s WHERE id = %s",
                    (new_status, acceptor_id, task_id),
                )
        else:
            with conn.cursor() as cursor:
                cursor.execute(
                    "UPDATE TASKS SET status = %s WHERE id = %s",
                    (new_status, task_id),
                )
        conn.commit()
        return jsonify({"status": "updated"}), 200
    except mysql.connector.Error as exc:
        return jsonify({"error": str(exc)}), 500
    finally:
        if conn is not None:
            conn.close()


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
