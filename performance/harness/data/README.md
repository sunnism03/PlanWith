# Runtime user data

Do not store a real `users.csv` in Git. Create it in this ignored directory or
another ignored path on the Mac that runs JMeter.

Required columns:

```text
userAlias,jwt,userId,categoryId,groupId,groupEventId
```

Example with placeholders only:

```text
perf-user-01,Bearer <runtime-jwt>,<user-uuid>,<category-uuid>,,
```

Use one distinct valid performance-test user per concurrent JMeter thread.
Never paste JWT values into reports, chat, JMeter logs, or tracked files.

