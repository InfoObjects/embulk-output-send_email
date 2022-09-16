#Send email output plugin for Embulk

An output plugin for Embulk to send email with data

## Configuration

- **send_email**: Required attribute for this output plugin
    - **to**: To whom you want to send email mention the email ID(required)
    - **from**: From which email ID you want to send (required)
    - **password**: Password of your email ID from which you want to send  (required)
    - **port**: Port of email (for gmail its `587`)(required)
    - **host**: Host of your email (For gmail `smtp.gmail.com`) (required)
    - **row**: How many row you want to send with email mention like 1,2,3(required)
    - **filetype**: Mention file type like (example: json,html) (required)
    
## Example - columns

Say input.csv is as follows:

​
```
    year               country_code                 country_name            literacy_rate
    
    1990                    1                          India                       80%
    1993                    2                           USA                        83%
    1997                    3                          JAPAN                        
    1999                    4                          China                       72%
    2000                    5                         Ukraine                      68%
    2002                    6                          Italy                       79%
    2004                    7                            UK                        75%
    2011                    8                           NULL                       42%
```
​


```yaml
out:
  type: send_email
  to: abc@example.com
  from: abc@example.com
  password: XXXXXX
  port: 587
  host: smtp.gmail.com
  row: 3
  filetype: html
```


## Development

Run example:

```
$ ./gradlew package
$ embulk run -I ./lib seed.yml
```

Deployment Steps:

```
Install ruby in your machine
$ gem install gemcutter (For windows OS)

$ ./gradlew gemPush
$ gem build NameOfYourPlugins (example: embulk-output-send_email)
$ gem push embulk-output-send_email-0.1.0.gem (You will get this name after running above command)
```


Release gem:

```
$ ./gradlew gemPush
```
