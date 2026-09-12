# Lambda Code

AWS Lambda responsável pela camada serverless de entrada da aplicação **Oficina Mecânica**, desenvolvida para o **Tech Challenge – Fase 3**.

A função atua integrada ao **Amazon API Gateway**, ao **Amazon RDS PostgreSQL** e ao backend da aplicação executado no **Amazon EKS**.

Sua principal responsabilidade é realizar a autenticação de clientes por CPF. As demais requisições são encaminhadas para o backend através de um proxy HTTP transparente.

---

## 📋 Sumário

* [Arquitetura](#-arquitetura)
* [Responsabilidades](#-responsabilidades)
* [Fluxo de autenticação](#-fluxo-de-autenticação)
* [Tecnologias](#-tecnologias)
* [Estrutura do projeto](#-estrutura-do-projeto)
* [Endpoint de autenticação](#-endpoint-de-autenticação)
* [Variáveis de ambiente](#-variáveis-de-ambiente)
* [AWS Secrets Manager](#-aws-secrets-manager)
* [JWT](#-jwt)
* [Proxy para o backend](#-proxy-para-o-backend)
* [Execução local](#-execução-local)
* [Testes](#-testes)
* [Build e empacotamento](#-build-e-empacotamento)
* [Deploy na AWS](#-deploy-na-aws)
* [Integração com a infraestrutura](#-integração-com-a-infraestrutura)
* [Segurança](#-segurança)
* [Limitações conhecidas](#-limitações-conhecidas)
* [Licença](#-licença)

---

## 🏗️ Arquitetura

A Lambda funciona como uma camada intermediária entre o cliente e os serviços da aplicação.

```text
                    ┌─────────────────────┐
                    │       Cliente       │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │   Amazon API        │
                    │      Gateway        │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │    AWS Lambda       │
                    │   ValidatorHandler  │
                    └───────┬─────┬───────┘
                            │     │
              POST /auth/cpf     │ Demais rotas
                            │     │
                            ▼     ▼
                 ┌────────────┐  ┌─────────────────┐
                 │ PostgreSQL │  │ Backend no EKS   │
                 │   owners   │  │ Oficina Mecânica │
                 └────────────┘  └─────────────────┘
                            │
                            ▼
                     JWT do cliente
```

### Componentes envolvidos

| Componente                | Responsabilidade                            |
| ------------------------- | ------------------------------------------- |
| **API Gateway**           | Ponto de entrada HTTP da aplicação          |
| **AWS Lambda**            | Validação de CPF, autenticação e proxy      |
| **Amazon RDS PostgreSQL** | Consulta dos clientes                       |
| **AWS Secrets Manager**   | Armazenamento das credenciais e segredo JWT |
| **Amazon EKS**            | Execução do backend principal               |
| **GitHub Actions**        | Automação do build e deploy da Lambda       |
| **Amazon S3**             | Armazenamento do artefato da Lambda         |

---

## 🎯 Responsabilidades

A Lambda possui dois comportamentos principais.

### 1. Autenticação por CPF

Para:

```http
POST /auth/cpf
```

a função:

1. recebe o CPF;
2. normaliza o documento;
3. valida os dígitos verificadores;
4. consulta o cliente na tabela `owners`;
5. verifica se o cliente existe;
6. gera um JWT;
7. retorna o token e os dados básicos do cliente.

A implementação do algoritmo de CPF foi mantida compatível com o algoritmo utilizado pelo monólito da aplicação.

### 2. Proxy para o backend

Para qualquer outra rota, a Lambda encaminha a requisição para o backend configurado em `BACKEND_URL`.

O proxy preserva:

* método HTTP;
* caminho;
* query parameters;
* corpo da requisição;
* headers compatíveis.

Headers hop-by-hop como `Host`, `Content-Length` e `Connection` são removidos antes do encaminhamento.

---

## 🔐 Fluxo de autenticação

O fluxo de autenticação do cliente ocorre da seguinte maneira:

```text
Cliente
   │
   │ POST /auth/cpf
   │ { "cpf": "123.456.789-09" }
   ▼
API Gateway
   │
   ▼
AWS Lambda
   │
   ├── Normaliza CPF
   │
   ├── Valida CPF
   │
   ├── Consulta RDS
   │      │
   │      └── SELECT ... FROM owners
   │
   ├── Cliente encontrado?
   │
   └── Gera JWT
          │
          ▼
       Resposta
```

Em caso de sucesso:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "ownerId": 123,
  "name": "Nome do Cliente"
}
```

---

## 🛠️ Tecnologias

* **Java 21**
* **Maven**
* **AWS Lambda**
* **Amazon API Gateway**
* **Amazon RDS PostgreSQL**
* **AWS Secrets Manager**
* **AWS SDK for Java 2.x**
* **JJWT**
* **Jackson**
* **JUnit 5**
* **Mockito**
* **GitHub Actions**

O projeto utiliza Java 21 e possui dependências específicas para execução no runtime da AWS Lambda, integração com Secrets Manager, PostgreSQL, JWT e testes automatizados.

---

## 📁 Estrutura do projeto

```text
lambda-code/
│
├── .github/
│   └── workflows/
│       └── ...
│
├── .mvn/
│   └── wrapper/
│
├── src/
│   ├── main/
│   │   └── java/
│   │       └── br/com/oficina/lambda/
│   │           ├── ValidatorHandler.java
│   │           ├── CpfValidator.java
│   │           ├── JwtService.java
│   │           ├── OwnerRepository.java
│   │           ├── HttpBackendProxyService.java
│   │           └── ...
│   │
│   └── test/
│       └── java/
│           └── br/com/oficina/lambda/
│               ├── CpfValidatorTest.java
│               ├── HttpBackendProxyServiceTest.java
│               ├── JwtServiceTest.java
│               └── ValidatorHandlerTest.java
│
├── pom.xml
├── mvnw
├── mvnw.cmd
├── LICENSE
└── README.md
```

---

## 🔌 Endpoint de autenticação

### `POST /auth/cpf`

Autentica um cliente utilizando seu CPF.

### Request

```http
POST /auth/cpf
Content-Type: application/json
```

```json
{
  "cpf": "123.456.789-09"
}
```

O CPF pode ser enviado formatado ou somente com números.

### Resposta de sucesso

```http
200 OK
```

```json
{
  "token": "JWT",
  "ownerId": 123,
  "name": "Nome do Cliente"
}
```

### Possíveis erros

#### CPF inválido

```http
400 Bad Request
```

```json
{
  "error": "INVALID_CPF",
  "message": "CPF invalido"
}
```

#### Cliente não encontrado

```http
404 Not Found
```

```json
{
  "error": "CLIENT_NOT_FOUND",
  "message": "Cliente nao encontrado para o CPF informado"
}
```

#### Erro interno

```http
500 Internal Server Error
```

```json
{
  "error": "INTERNAL_ERROR",
  "message": "Falha ao consultar cliente"
}
```

---

## ⚙️ Variáveis de ambiente

A Lambda utiliza as seguintes variáveis:

| Variável                       | Obrigatória | Descrição                                                               |
| ------------------------------ | ----------: | ----------------------------------------------------------------------- |
| `DATABASE_HOST`                |           ✅ | Host do PostgreSQL                                                      |
| `DATABASE_PORT`                |           ❌ | Porta do PostgreSQL. Padrão: `5432`                                     |
| `DATABASE_NAME`                |           ❌ | Nome do banco. Padrão: `workshop`                                       |
| `DATABASE_USER_SECRET_ARN`     |           ✅ | ARN do Secret Manager contendo o usuário do banco                       |
| `DATABASE_PASSWORD_SECRET_ARN` |           ✅ | ARN do Secret Manager contendo a senha do banco                         |
| `JWT_SECRET_ARN`               |           ✅ | ARN do Secret Manager contendo o segredo utilizado para assinar os JWTs |
| `BACKEND_URL`                  |           ✅ | URL do backend executado no EKS                                         |

Exemplo:

```text
DATABASE_HOST=my-database.xxxxxx.us-east-1.rds.amazonaws.com
DATABASE_PORT=5432
DATABASE_NAME=workshop

DATABASE_USER_SECRET_ARN=arn:aws:secretsmanager:us-east-1:123456789012:secret:...
DATABASE_PASSWORD_SECRET_ARN=arn:aws:secretsmanager:us-east-1:123456789012:secret:...
JWT_SECRET_ARN=arn:aws:secretsmanager:us-east-1:123456789012:secret:...

BACKEND_URL=http://backend-oficina-mecanica
```

> Os valores reais de credenciais e segredos **não devem ser armazenados no código-fonte ou no GitHub**.

---

## 🔑 AWS Secrets Manager

Informações sensíveis são recuperadas através do **AWS Secrets Manager**.

A Lambda não recebe diretamente no código:

* usuário do banco;
* senha do banco;
* segredo utilizado para assinatura do JWT.

Durante a inicialização, o `ValidatorHandler` obtém os valores através dos respectivos Secrets Manager ARNs.

```text
Lambda
   │
   ├── DATABASE_USER_SECRET_ARN
   │             │
   │             ▼
   │      AWS Secrets Manager
   │
   ├── DATABASE_PASSWORD_SECRET_ARN
   │             │
   │             ▼
   │      AWS Secrets Manager
   │
   └── JWT_SECRET_ARN
                 │
                 ▼
          AWS Secrets Manager
```

A role utilizada pela Lambda deve possuir as permissões necessárias para consultar esses secrets.

---

## 🪪 JWT

O JWT emitido pela Lambda foi implementado para ser compatível com o mecanismo de autenticação utilizado pelo monólito.

Características:

| Propriedade    | Valor                      |
| -------------- | -------------------------- |
| Algoritmo      | HS256                      |
| Issuer         | `mechanic-workshop-system` |
| Role           | `CLIENTE`                  |
| Subject        | CPF do cliente             |
| Claim `userId` | ID do cliente              |
| Expiração      | 30 minutos                 |

Exemplo conceitual do payload:

```json
{
  "sub": "12345678909",
  "iss": "mechanic-workshop-system",
  "roles": [
    "CLIENTE"
  ],
  "userId": 123,
  "iat": 1720000000,
  "exp": 1720001800
}
```

O segredo utilizado para assinatura é recuperado do AWS Secrets Manager.

---

## 🔄 Proxy para o backend

As requisições que não correspondem a:

```text
POST /auth/cpf
```

são encaminhadas para o backend.

Por exemplo:

```text
Cliente
   │
   │ GET /owners/123
   ▼
API Gateway
   │
   ▼
Lambda
   │
   │ GET BACKEND_URL/owners/123
   ▼
Backend no EKS
```

O proxy utiliza o `HttpClient` nativo do Java e possui:

* timeout de conexão de 5 segundos;
* timeout de requisição de 10 segundos;
* propagação dos headers;
* propagação do body;
* propagação do status HTTP;
* propagação dos headers da resposta.

Caso o backend não possa ser acessado, a Lambda retorna:

```http
502 Bad Gateway
```

```json
{
  "error": "BACKEND_UNAVAILABLE",
  "message": "Nao foi possivel contatar o backend"
}
```

---

## 💻 Execução local

### Pré-requisitos

* Java 21+
* Maven 3.9+
* acesso às dependências Maven
* credenciais AWS, caso sejam executados testes que dependam de serviços AWS

Verifique:

```bash
java -version
mvn -version
```

### Clonar o projeto

```bash
git clone https://github.com/Grupo-SOAT/lambda-code.git
cd lambda-code
```

### Compilar

Linux/macOS:

```bash
./mvnw clean package
```

Windows:

```cmd
mvnw.cmd clean package
```

Ou utilizando Maven instalado:

```bash
mvn clean package
```

---

## 🧪 Testes

O projeto possui testes unitários utilizando **JUnit 5** e **Mockito**.

Entre os componentes testados estão:

* validação de CPF;
* geração de JWT;
* `ValidatorHandler`;
* proxy HTTP para o backend.

Para executar:

```bash
./mvnw test
```

No Windows:

```cmd
mvnw.cmd test
```

---

## 📦 Build e empacotamento

O projeto utiliza o Maven Shade Plugin para gerar um **fat JAR**, contendo a aplicação e suas dependências.

Executar:

```bash
./mvnw clean package
```

O artefato principal é gerado como:

```text
target/lambda-code.jar
```

Esse artefato pode ser utilizado como pacote da AWS Lambda.

A configuração atual do `pom.xml` foi preparada especificamente para gerar um JAR que também pode ser tratado como ZIP válido para o empacotamento da função Lambda.

---

## ☁️ Deploy na AWS

O deploy da função é realizado utilizando infraestrutura AWS provisionada via Terraform.

O fluxo geral é:

```text
GitHub
   │
   │ push / workflow_dispatch
   ▼
GitHub Actions
   │
   ├── Checkout
   │
   ├── Java 21
   │
   ├── Maven build
   │
   ├── mvn clean package
   │
   └── Upload do artefato
             │
             ▼
       Amazon S3
             │
             ▼
       AWS Lambda
```

O artefato gerado pelo Maven é armazenado em um bucket S3 utilizado para disponibilizar o código da função.

A infraestrutura da Lambda é gerenciada separadamente pelo repositório de infraestrutura da aplicação.

---

## 🔗 Integração com a infraestrutura

Este repositório é responsável pelo **código da função**.

A infraestrutura necessária para executar a Lambda é provisionada separadamente.

A integração envolve:

```text
lambda-code
     │
     │ artefato Java
     ▼
Amazon S3
     │
     ▼
AWS Lambda
     │
     ├──────────────► AWS Secrets Manager
     │
     ├──────────────► Amazon RDS PostgreSQL
     │
     └──────────────► Backend no Amazon EKS
```

A infraestrutura Terraform é responsável por configurar recursos como:

* AWS Lambda;
* IAM Role;
* API Gateway;
* S3;
* Secrets Manager;
* integração com o banco;
* variáveis de ambiente.

---

## 🔒 Segurança

Algumas práticas importantes adotadas neste projeto:

### Segredos fora do código

Credenciais do banco e o segredo JWT são armazenados no AWS Secrets Manager.

### JWT

Os tokens são assinados utilizando HS256 e possuem expiração definida.

### Prepared Statements

A consulta ao PostgreSQL utiliza `PreparedStatement`, evitando concatenação direta do CPF na query SQL.

```sql
SELECT owner_id, name, document, email
FROM owners
WHERE document = ?
```

### Headers

O proxy não encaminha headers HTTP hop-by-hop como:

```text
Host
Content-Length
Connection
```

### Princípio do menor privilégio

A IAM Role da Lambda deve possuir somente as permissões necessárias para:

* execução da função;
* leitura dos Secrets Manager utilizados;
* acesso aos recursos necessários à execução.

---

## ⚠️ Limitações conhecidas

Atualmente, a tabela `owners` não possui um campo específico indicando se o cliente está ativo.

Por isso, a existência do CPF na tabela é utilizada como indicação de que o cliente pode ser autenticado.

Atualmente a consulta é equivalente a:

```sql
SELECT owner_id, name, document, email
FROM owners
WHERE document = ?
```

Caso futuramente seja adicionado um campo como:

```text
active
```

a consulta poderá ser adaptada para considerar somente clientes ativos.

---

## 📌 Decisões importantes

### Lambda como camada de entrada

A Lambda concentra a autenticação específica por CPF e evita alterar o mecanismo de autenticação já existente no backend.

### Compatibilidade com o monólito

O algoritmo de CPF e a estrutura do JWT foram implementados para manter compatibilidade com o monólito existente.

### Backend desacoplado

As demais rotas continuam sendo processadas pelo backend principal, permitindo que a Lambda funcione como uma camada de entrada sem duplicar as regras de negócio da aplicação.

---

## 📚 Projeto

Este repositório faz parte da solução **Oficina Mecânica – Tech Challenge Fase 3**, desenvolvida pelo grupo **Grupo-SOAT**.

### Repositório

[Grupo-SOAT/lambda-code](https://github.com/Grupo-SOAT/lambda-code)

---

## 📄 Licença

Este projeto está licenciado sob a licença **MIT**.

Consulte o arquivo [`LICENSE`](./LICENSE) para mais informações.
