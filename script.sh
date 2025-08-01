#!/bin/bash

# Check that at least two arguments are provided
if [ $# -lt 2 ]; then
    echo "Usage: $0 <arg1> <arg2>"
    exit 1
fi

arg1="$1"  # e.g. 0
arg2="$2"  # e.g. 10

# Function to clean compiled Java classes
clean_out_folder() {
    echo "Cleaning compiled class files in out/..."
    rm -rf out/*
}

while true; do
    echo "=============================="
    echo "Compiling the Java program..."

    clean_out_folder
    javac -cp "lib/*" -d out src/main/java/finalagent/*.java

    if [ $? -ne 0 ]; then
        echo "Java compilation failed. Retrying in 5 seconds..."
        sleep 5
        continue
    fi

    # Get local IP address (excluding 127.0.0.1)
    local_ip=$(ifconfig | grep -w inet | grep -v 127.0.0.1 | awk '{print $2}' | head -n 1)
    last_octet=$(echo "$local_ip" | cut -d '.' -f 4)

    # Search for an active machine with port 60000 open
    remote_ip=$(sudo nmap -p 60000 --open -oG - 192.168.224.0/24 | awk '/Up$/{print $2}' | head -n 1)

    echo "Launching Java program with:"
    echo " - lastOctet: $last_octet"
    echo " - arg1: $arg1"
    echo " - arg2: $arg2"
    echo " - remote IP: $remote_ip"

    if [ -n "$remote_ip" ]; then
        java -cp "out:lib/*" finalagent.Main "$last_octet" "$arg1" "$arg2" "$remote_ip"
    else
        java -cp "out:lib/*" finalagent.Main "$last_octet" "$arg1" "$arg2"
    fi

    exit_code=$?
    echo "Java program exited (code $exit_code). Restarting in 15 seconds..."
    sleep 15
done
