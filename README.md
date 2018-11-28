# Diplomacy Slack App
Simple Slack app for managing diplomacy games. 

When a GET or POST request hits`/slack/reset` it will kick every user from all channels, delete the channel and remove
all messages if the channel cannot be delete. When it is done, it will send a message saying the Slack group is ready for 
a new game to #General.

## Building
Run `./gradlew build`, fat jar will be located in `/build/libs/`

## Running
To run: `java -jar diplomacy-slack-app-1.0-SNAPSHOT-fat.jar -conf ${path/to/configuration.json}`

The configuration file is expected be a json file with the following format
```json
{
  "HTTP.PORT": 8080,
  "SLACK_TOKEN": "slack.app.token",
  "SLACK_HOOK_URL": "/slack/web/hook/url"
}
```