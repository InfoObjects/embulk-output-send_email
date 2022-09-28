<p align="center">
  <a href="https://www.infoobjects.com/" target="blank"><img src="screenshots/logo.png" width="150" alt="InfoObjects Logo" /></a>
</p>
<p align="center">Infoobjects is a consulting company that helps enterprises transform how and where they run applications and infrastructure.
From strategy, to implementation, to ongoing managed services, Infoobjects creates tailored cloud solutions for enterprises at all stages of the cloud journey.</p>

#Send email output plugin for Embulk
[![license](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

An output plugin for Embulk to send email with data

## Configuration

- **send_email**: Required attribute for this output plugin
    - **to**: To whom you want to send email mention the email ID(required)
    - **cc**: Mail id of others to send carbon copy
    - **from**: From which email ID you want to send (required)
    - **password**: Password of your email ID from which you want to send  (required)
    - **port**: Port of email (for gmail its `587`)(required)
    - **username**: Username of authentic user (required)
    - **host**: Host of your email (For gmail `smtp.gmail.com`) (required)
    - **subject**: Subject for the mail body
    - **row**: How many row you want to send with email mention like 1,2,3
    - **format_type**: Mention file type like (example: json,html) (required)
    - **protocol**: TLSv1.2 (required)
    - **auth**: When authentication is required make it 'true' otherwise default it is 'false'
    - **enable_starttls**: true/ false (required)
    - **template**: If format_type is 'html', provide here the path of .html/ .txt
    - **is_html**: If template is 'html' put it true, if it is 'txt' put it as false, by default it is false

```
NOTE: If format_type is html and path is given in field 'template', make sure - The templates should have '{{data}}' placeholder so that the data generated by send_email plugin will be put here; sample .html and .txt is shown below -

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Title</title>
</head>
<body>
<header>
    Hi Team,
    <h3>Here is the project</h3>
</header>
<h1> hello </h1>

{{data}}

<footer>
    Thanks,<br>
    ABC
</footer>
</body>
</html>

-------------------------------------------

Hi John,
Here is the daily ETL data

{{data}}

Thanks,
ABC


```

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
  to:
    - abc@gmail.com
    - def@gmail.com
  from: pqr@gmail.com
  password: password
  port: 587
  username: pqr@gmail.com
  host: smtp.gmail.com
  subject: XYZ123
  row: 3
  format_type: html
  protocol: TLSv1.2
  auth: true
  enable_starttls: true
  template: C:\Users\Abhishek Gupta\Desktop\github\embulk-output-send_email\example\email.txt
  is_html: false
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
## Licensing

InfoObjects [license](LICENSE) (MIT License)