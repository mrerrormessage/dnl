# Distributed NetLogo

## Compiling

## Usage

```
dnl:info => "tcp://127.0.0.1:9393"
```

```
dnl:report "tcp://127.0.0.1:9393" "count sheep" => 57
```

```
dnl:command "tcp://127.0.0.1:9393" "ask sheep [set color black]"
```

### Errors

When the command/reporter is unable to connect to the remote server, you can expect to see an error like:

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
