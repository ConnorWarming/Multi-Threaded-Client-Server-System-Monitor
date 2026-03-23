
---

# Multi-Threaded Client-Server System Monitor

```markdown
# Multi-Threaded Client-Server System Monitor

## Overview
A Java-based client-server application that executes remote system commands and measures performance using concurrent requests.

## Features
- Client-server communication using sockets
- Multi-threaded request handling
- Thread pool implementation on server
- Execute system commands remotely:
  - Date/Time
  - Uptime
  - Memory usage
  - Processes
- Performance measurement (turnaround time)
- Concurrent request testing

## Technologies Used
- Java
- Sockets (Networking)
- Multithreading
- ExecutorService

## How to Run

### Compile
```bash
javac Server.java
javac Client.java

### Run
java Server
java Client
