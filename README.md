# Distributed NetLogo

## Compiling

## Usage


### DNL Administration
```
dnl:info => "tcp://127.0.0.1:9393"
```

### Reporters
```
dnl:report "tcp://127.0.0.1:9393" "count sheep" => 57
dnl:report ["tcp://127.0.0.1:9393" "tcp://127.0.0.1:9394"] "count sheep" => [57 67]
```

### Commands
```
dnl:command "tcp://127.0.0.1:9393" "ask sheep [set color black]"
dnl:command ["tcp://127.0.0.1:9393" "tcp://127.0.0.1:9394"] "ask sheep [ set color black ]"
```

```
;; Synchronous command, does not return until command has finished on remote(s)
dnl:command-sync "tcp://127.0.0.1:9393" "ask sheep [set color black]"
dnl:command-sync ["tcp://127.0.0.1:9393" "tcp://127.0.0.1:9394"] "ask sheep [ set color black ]"
```

### Errors

When the command/reporter is unable to connect to the remote server(s) you can expect to see an error like:

```
DNL Timeout: response timeout
DNL Timeout: unable to connect
```

When the command/reporter raised an unexpected exception, you can expect to see an error like:

```
DNL Remote Exception: <text of remote exception>
```

It is recommended that you handle errors using `carefully`

## Terms of Use
