# Gradle Wrapper (sans binaire versionné)

Ce dépôt versionne volontairement uniquement les fichiers texte du wrapper :
- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.properties`

Le binaire `gradle-wrapper.jar` n'est pas commité ici.

Pour le régénérer localement (si nécessaire) :

```bash
cd android-app
gradle wrapper
```

Ensuite, vous pouvez lancer :

```bash
./gradlew tasks
```
