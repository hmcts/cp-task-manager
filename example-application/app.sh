#!/bin/bash

# Application Management Script for Task Manager Application
# Usage: ./app.sh {start|stop|restart|status|curl}

APP_NAME="Task Manager Application"
APP_PORT=8080
BASE_URL="http://localhost:${APP_PORT}"
PID_FILE=".app.pid"
LOG_FILE="app.log"
GRADLE_TASK=":example-application:bootRun"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored messages
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check if application is running
is_running() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p "$PID" > /dev/null 2>&1; then
            return 0
        else
            rm -f "$PID_FILE"
            return 1
        fi
    fi
    
    # Also check by port
    if lsof -Pi :${APP_PORT} -sTCP:LISTEN -t >/dev/null 2>&1; then
        return 0
    fi
    
    return 1
}

# Function to get PID from port
get_pid_from_port() {
    lsof -ti :${APP_PORT} 2>/dev/null | head -1
}

# Function to start the application
start_app() {
    if is_running; then
        print_warn "${APP_NAME} is already running (PID: $(cat "$PID_FILE" 2>/dev/null || get_pid_from_port))"
        return 1
    fi
    
    print_info "Starting ${APP_NAME}..."
    
    # Navigate to project root
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
    cd "$PROJECT_ROOT" || exit 1
    
    # Start application in background
    nohup ./gradlew ${GRADLE_TASK} > "${SCRIPT_DIR}/${LOG_FILE}" 2>&1 &
    GRADLE_PID=$!
    
    # Wait a bit and find the actual Java process
    sleep 3
    JAVA_PID=$(get_pid_from_port)
    
    if [ -n "$JAVA_PID" ]; then
        echo "$JAVA_PID" > "${SCRIPT_DIR}/${PID_FILE}"
        print_info "${APP_NAME} started successfully (PID: $JAVA_PID)"
        print_info "Logs are being written to: ${SCRIPT_DIR}/${LOG_FILE}"
        print_info "Application will be available at: ${BASE_URL}"
        
        # Wait a bit more and check if it's actually responding
        sleep 5
        if curl -s "${BASE_URL}/api/jobs" > /dev/null 2>&1; then
            print_info "Application is responding to requests"
        else
            print_warn "Application started but may still be initializing. Check logs: ${SCRIPT_DIR}/${LOG_FILE}"
        fi
    else
        print_error "Failed to start ${APP_NAME}. Check logs: ${SCRIPT_DIR}/${LOG_FILE}"
        return 1
    fi
}

# Function to stop the application
stop_app() {
    if ! is_running; then
        print_warn "${APP_NAME} is not running"
        return 1
    fi
    
    print_info "Stopping ${APP_NAME}..."
    
    # Try to get PID from file first
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
    else
        PID=$(get_pid_from_port)
    fi
    
    if [ -z "$PID" ]; then
        print_error "Could not find process ID"
        return 1
    fi
    
    # Try graceful shutdown first
    print_info "Sending TERM signal to process $PID..."
    kill -TERM "$PID" 2>/dev/null
    
    # Wait for process to stop
    for i in {1..10}; do
        if ! ps -p "$PID" > /dev/null 2>&1; then
            break
        fi
        sleep 1
    done
    
    # Force kill if still running
    if ps -p "$PID" > /dev/null 2>&1; then
        print_warn "Process did not stop gracefully, forcing shutdown..."
        kill -9 "$PID" 2>/dev/null
        sleep 1
    fi
    
    # Clean up PID file
    rm -f "$PID_FILE"
    
    # Verify it's stopped
    if ! is_running; then
        print_info "${APP_NAME} stopped successfully"
    else
        print_error "Failed to stop ${APP_NAME}"
        return 1
    fi
}

# Function to restart the application
restart_app() {
    print_info "Restarting ${APP_NAME}..."
    stop_app
    sleep 2
    start_app
}

# Function to show application status
show_status() {
    if is_running; then
        PID=$(cat "$PID_FILE" 2>/dev/null || get_pid_from_port)
        print_info "${APP_NAME} is running (PID: $PID)"
        print_info "Application URL: ${BASE_URL}"
        
        # Check if it's responding
        if curl -s "${BASE_URL}/api/jobs" > /dev/null 2>&1; then
            print_info "Application is responding to requests"
        else
            print_warn "Application is running but not responding to requests"
        fi
    else
        print_warn "${APP_NAME} is not running"
    fi
}

# Function to show logs
show_logs() {
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    LOG_PATH="${SCRIPT_DIR}/${LOG_FILE}"
    
    if [ -f "$LOG_PATH" ]; then
        print_info "Showing last 50 lines of logs (use 'tail -f ${LOG_PATH}' for live logs):"
        tail -50 "$LOG_PATH"
    else
        print_warn "Log file not found: ${LOG_PATH}"
    fi
}

# Function to execute curl commands
execute_curl() {
    if ! is_running; then
        print_error "${APP_NAME} is not running. Please start it first."
        return 1
    fi
    
    print_info "Available API endpoints:"
    echo ""
    echo "1. GET /api/jobs - Get all jobs"
    echo "2. GET /api/jobs/{jobId} - Get job by ID"
    echo "3. POST /api/jobs - Create a new job"
    echo ""
    
    case "${2:-all}" in
        "all"|"")
            print_info "Fetching all jobs..."
            curl -s -X GET "${BASE_URL}/api/jobs" | jq '.' 2>/dev/null || curl -s -X GET "${BASE_URL}/api/jobs"
            ;;
        "get")
            if [ -z "$3" ]; then
                print_error "Usage: ./app.sh curl get {jobId}"
                return 1
            fi
            print_info "Fetching job with ID: $3"
            curl -s -X GET "${BASE_URL}/api/jobs/$3" | jq '.' 2>/dev/null || curl -s -X GET "${BASE_URL}/api/jobs/$3"
            ;;
        "create"|"post")
            print_info "Creating a new job..."
            curl -s -X POST "${BASE_URL}/api/jobs" -H "Content-Type: application/json"
            echo ""
            print_info "Job created. Fetching all jobs..."
            sleep 1
            curl -s -X GET "${BASE_URL}/api/jobs" | jq '.' 2>/dev/null || curl -s -X GET "${BASE_URL}/api/jobs"
            ;;
        *)
            print_error "Unknown curl command: $2"
            echo "Usage: ./app.sh curl [all|get {jobId}|create]"
            return 1
            ;;
    esac
}

# Main script logic
case "${1:-}" in
    "start")
        start_app
        ;;
    "stop")
        stop_app
        ;;
    "restart")
        restart_app
        ;;
    "status")
        show_status
        ;;
    "logs")
        show_logs
        ;;
    "curl")
        execute_curl "$@"
        ;;
    *)
        echo "Usage: $0 {start|stop|restart|status|logs|curl [command]}"
        echo ""
        echo "Commands:"
        echo "  start   - Start the application"
        echo "  stop    - Stop the application"
        echo "  restart - Restart the application"
        echo "  status  - Show application status"
        echo "  logs    - Show application logs"
        echo "  curl    - Execute curl commands to test API"
        echo ""
        echo "Curl subcommands:"
        echo "  curl all          - Get all jobs (default)"
        echo "  curl get {jobId}  - Get job by ID"
        echo "  curl create       - Create a new job"
        echo ""
        exit 1
        ;;
esac

exit $?

