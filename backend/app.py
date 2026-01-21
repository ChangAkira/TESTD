import os

from flask import Flask, jsonify, request
import mysql.connector
from mysql.connector import errorcode


app = Flask(__name__)

DB_CONFIG = {
    "host": os.getenv("DB_HOST", "localhost"),
    "user": os.getenv("DB_USER", "root"),
    "password": os.getenv("DB_PASSWORD", "111111"),
    "database": os.getenv("DB_NAME", "testd"),
    "autocommit": False,
}


def get_connection():
    return mysql.connector.connect(**DB_CONFIG)


def ensure_table_exists(conn):
    create_sql = """
    CREATE TABLE IF NOT EXISTS USERINFOS (
        student_id VARCHAR(64) PRIMARY KEY,
        name VARCHAR(128),
        username VARCHAR(128)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    """
    with conn.cursor() as cursor:
        cursor.execute(create_sql)
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
        with conn.cursor() as cursor:
            insert_sql = """
            INSERT INTO USERINFOS (student_id, name, username)
            VALUES (%s, %s, %s)
            """
            cursor.execute(insert_sql, (student_id, name, username))
        conn.commit()
        return jsonify({"status": "created"}), 201
    except mysql.connector.Error as exc:
        if exc.errno == errorcode.ER_DUP_ENTRY:
            return jsonify({"status": "exists"}), 409
        return jsonify({"error": str(exc)}), 500
    finally:
        if conn is not None:
            conn.close()


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
