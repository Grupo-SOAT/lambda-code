# Lambda Code

AWS Lambda responsável pela camada serverless de entrada da aplicação **Oficina Mecânica**, desenvolvida para o **Tech Challenge – Fase 3**.

A função atua integrada ao **Amazon API Gateway**, ao **Amazon RDS PostgreSQL** e ao backend da aplicação executado no **Amazon EKS**.

Este repositório contém duas funções:

* **`ValidatorHandler`** — autentica clientes por CPF (`POST /auth/cpf`) e emite o JWT;
* **`AuthorizerHandler`** — Lambda Authorizer do API Gateway: valida o JWT nas demais rotas, que são repassadas **direto para o backend** por uma integração `HTTP_PROXY` do próprio API Gateway (a Lambda não faz mais proxy de nenhuma requisição de negócio).

---

## 📋 Sumário

* [Arquitetura](#-arquitetura)
* [Responsabilidades](#-responsabilidades)
* [Fluxo de autenticação](#-fluxo-de-autenticação)
* [Tecnologias](#-tecnologias)
* [Estrutura do projeto](#-estrutura-do-projeto)
* [Endpoint de autenticação](#-endpoint-de-autenticação)
* [Variáveis de ambiente](#-variáveis-de-ambiente)
* [Segredos](#-segredos)
* [JWT](#-jwt)
* [Lambda Authorizer](#-lambda-authorizer)
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

O API Gateway só invoca a `ValidatorHandler` na rota de autenticação. Nas demais rotas ele fala **direto** com o backend (integração `HTTP_PROXY`), usando a `AuthorizerHandler` apenas para decidir se autoriza a chamada.

```text
                    ┌─────────────────────┐
                    │       Cliente       │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │   Amazon API        │
                    │      Gateway        │
                    └───────┬──────┬──────┘
                            │      │
              POST /auth/cpf      │ Demais rotas (HTTP_PROXY)
                            │      │
                            ▼      ├──────────────────┐
                 ┌─────────────────┐│                  ▼
                 │ ValidatorHandler││       ┌────────────────────┐
                 └───────┬─────────┘│       │ AuthorizerHandler  │
                         │          │       │ (valida o JWT)     │
                         ▼          │       └────────────────────┘
                 ┌────────────┐     │
                 │ PostgreSQL │     ▼
                 │   owners   │  ┌─────────────────┐
                 └────────────┘  │ Backend no EKS   │
                                  │ Oficina Mecânica │
                                  └─────────────────┘
```

### Componentes envolvidos

| Componente                | Responsabilidade                                                           |
| ------------------------- | --------------------------------------------------------------------------- |
| **API Gateway**           | Ponto de entrada HTTP; roteia `/auth` pra Lambda, o resto direto pro backend |
| **`ValidatorHandler`**    | Validação de CPF e emissão do JWT                                          |
| **`AuthorizerHandler`**   | Validação do JWT nas rotas de negócio (Lambda Authorizer)                  |
| **Amazon RDS PostgreSQL** | Consulta dos clientes (VPC)                                                  |
| **Amazon EKS**            | Execução do backend principal                                               |
| **GitHub Actions**        | Automação do build e deploy da Lambda       |
| **Amazon S3**             | Armazenamento do artefato da Lambda         |

---

## 🎯 Responsabilidades

Este repositório implementa duas funções com responsabilidades separadas.

### 1. Autenticação por CPF (`ValidatorHandler`)

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

### 2. Autorização das rotas de negócio (`AuthorizerHandler`)

Para qualquer outra rota, o API Gateway já fala direto com o backend (integração `HTTP_PROXY`, configurada em `modules/aws/gateway` no repo `k8s-infra-oficina-mecanica`) - a Lambda não participa dessa chamada. Antes de liberar a rota, o Gateway invoca a `AuthorizerHandler`:

1. lê o header `Authorization: Bearer <token>`;
2. valida a assinatura e a expiração do JWT;
3. responde `{ "isAuthorized": true, "context": {...} }` (libera) ou `{ "isAuthorized": false }` (nega, o Gateway responde `403`).

O API Gateway cacheia essa decisão por `authorizer_result_ttl_in_seconds` (0s (desativado por padrão) por padrão), então o mesmo token não invoca a Lambda a cada chamada.

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
* **Amazon VPC** (a `ValidatorHandler` roda na VPC; a `AuthorizerHandler` não)
* **JJWT**
* **Jackson**
* **JUnit 5**
* **Mockito**
* **GitHub Actions**

O projeto utiliza Java 21 e possui dependências específicas para execução no runtime da AWS Lambda, integração com PostgreSQL, JWT e testes automatizados.

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
│   │           ├── AuthorizerHandler.java
│   │           ├── CpfValidator.java
│   │           ├── JwtService.java
│   │           ├── OwnerRepository.java
│   │           └── ...
│   │
│   └── test/
│       └── java/
│           └── br/com/oficina/lambda/
│               ├── CpfValidatorTest.java
│               ├── AuthorizerHandlerTest.java
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

| Variável            | Obrigatória | Descrição                                        |
| ------------------- | ----------: | ------------------------------------------------ |
| `DATABASE_HOST`     |           ✅ | Host do PostgreSQL (Amazon RDS)                  |
| `DATABASE_PORT`     |           ❌ | Porta do PostgreSQL. Padrão: `5432`              |
| `DATABASE_NAME`     |           ❌ | Nome do banco. Padrão: `workshop`                |
| `DATABASE_USER`     |           ✅ | Usuário do banco                                 |
| `DATABASE_PASSWORD` |           ✅ | Senha do banco                                   |
| `JWT_SECRET`        |           ✅ | Segredo utilizado para assinar/validar os JWTs   |

A `AuthorizerHandler` lê apenas `JWT_SECRET`. A `ValidatorHandler` lê todas as variáveis acima.

Exemplo:

```text
DATABASE_HOST=my-database.xxxxxx.us-east-1.rds.amazonaws.com
DATABASE_PORT=5432
DATABASE_NAME=workshop
DATABASE_USER=db_admin
DATABASE_PASSWORD=...
JWT_SECRET=...
```

> Os valores reais de credenciais e segredos **não devem ser armazenados no código-fonte ou no GitHub**.

---

## 🔑 Segredos

As credenciais do banco e o segredo JWT **não são recuperados do AWS Secrets Manager**. O Terraform injeta os valores como **variáveis de ambiente** da função, a partir de GitHub Secrets de organização. A Lambda não busca segredos em runtime nem mantém cache de credenciais.

A `ValidatorHandler` roda dentro da **VPC** (security group liberando apenas `5432` para o RDS e `443` para um VPC endpoint de CloudWatch Logs). A `AuthorizerHandler` não acessa banco nem rede.

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

O segredo utilizado para assinatura vem da variável de ambiente `JWT_SECRET` (mesmo valor configurado no monólito).

---

## 🛂 Lambda Authorizer

As requisições que não correspondem a `POST /auth/cpf` são repassadas pelo próprio API Gateway direto para o backend (integração `HTTP_PROXY`) - a `AuthorizerHandler` só decide se autoriza, ela não vê o corpo nem repassa a chamada.

```text
Cliente
   │
   │ GET /owners/123
   │ Authorization: Bearer <jwt>
   ▼
API Gateway ──────► AuthorizerHandler (valida o JWT)
   │                        │
   │        isAuthorized: true/false
   │◄───────────────────────┘
   │
   │ (se autorizado) GET backend/owners/123
   ▼
Backend no EKS
```

Evento recebido (formato "simple response", payload 2.0):

```json
{ "headers": { "authorization": "Bearer eyJhbGciOiJIUzI1NiJ9..." } }
```

Resposta em caso de token válido:

```json
{
  "isAuthorized": true,
  "context": { "sub": "12345678909", "userId": "123", "roles": "CLIENTE" }
}
```

Token ausente, malformado, expirado ou assinado com outro segredo:

```json
{ "isAuthorized": false }
```

Nesse caso o próprio API Gateway responde `403` ao cliente, sem a Lambda precisar formatar nenhum corpo de erro.

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
* geração e verificação de JWT;
* `ValidatorHandler`;
* `AuthorizerHandler`.

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

O workflow **`Lambda CI and deploy`** (`.github/workflows/deploy.yaml`) roda testes em Pull Requests e, em push para `main` (environment `producao`) ou `homologacao`, publica o artefato e atualiza as funções:

```text
GitHub (push main/homologacao)
   │
   ▼
GitHub Actions
   │
   ├── Maven build (mvn clean verify)
   ├── zip (lib/lambda-code.jar)
   └── upload S3  →  <environment>/<sha>/lambda.zip
             │
             ├── se a função existe: update-function-code (validator + authorizer)
             │
             └── repository_dispatch "lambda-updated" → k8s-infra-oficina-mecanica
```

No primeiro bootstrap as funções ainda não existem: o artefato é enviado ao S3 e o `repository_dispatch` faz o Terraform criar as duas funções (`oficina-mecanica-validator` e `oficina-mecanica-jwt-authorizer`) já com esse código. Depois disso o workflow passa a atualizar o código diretamente. O Terraform mantém `handler`/configuração e ignora mudanças posteriores de `s3_key`/`source_code_hash`.

A conta AWS é descoberta via `sts get-caller-identity`; bucket e nomes das funções vêm das variáveis de organização `TF_LAMBDA_BUCKET`, `LAMBDA_VALIDATOR_NAME` e `LAMBDA_AUTHORIZER_NAME`.

---

## 🔗 Integração com a infraestrutura

Este repositório é responsável pelo **código da função**.

A infraestrutura necessária para executar a Lambda é provisionada separadamente.

A integração envolve:

```text
lambda-code
     │
     │ artefato Java (jar/zip)
     ▼
Amazon S3
     │
     ▼
AWS Lambda  (recebe DATABASE_* e JWT_SECRET como env)
     │
     ├──────────────► Amazon RDS PostgreSQL (via VPC)
     │
     └──────────────► API Gateway (invocação + Lambda Authorizer)
```

A infraestrutura Terraform (no repositório `k8s-infra-oficina-mecanica`) é responsável por configurar recursos como:

* AWS Lambda (as duas funções);
* API Gateway + Lambda Authorizer;
* S3 (bucket do artefato);
* VPC/security groups/VPC endpoint de logs para a `validator`;
* leitura do endpoint do RDS via `data.aws_db_instance`;
* variáveis de ambiente (credenciais e `JWT_SECRET`).

---

## 🔒 Segurança

Algumas práticas importantes adotadas neste projeto:

### Segredos fora do código

Credenciais do banco e o segredo JWT chegam como variáveis de ambiente injetadas pelo Terraform a partir de GitHub Secrets de organização; não há segredos versionados nem leitura de AWS Secrets Manager em runtime.

### JWT

Os tokens são assinados utilizando HS256 e possuem expiração definida.

### Prepared Statements

A consulta ao PostgreSQL utiliza `PreparedStatement`, evitando concatenação direta do CPF na query SQL.

```sql
SELECT owner_id, name, document, email
FROM owners
WHERE document = ?
```

### Princípio do menor privilégio

A IAM Role da Lambda deve possuir somente as permissões necessárias para:

* execução da função;
* acesso aos recursos necessários à execução.

A `ValidatorHandler` tem egress de rede restrito ao RDS (`5432`) e ao VPC endpoint de logs (`443`).

---

## ⚠️ Limitações conhecidas

A autenticação consulta `owners.active` e bloqueia clientes inativos. Em um banco novo a coluna `active` é criada automaticamente pelo Hibernate (`ddl-auto=update`, default `true`) junto com o seed; o script `docs/migrations/001-owner-active.sql` só é necessário para bancos pré-existentes criados antes dessa coluna.

---

## 📌 Decisões importantes

### Lambda como camada de entrada

A Lambda concentra a autenticação específica por CPF e evita alterar o mecanismo de autenticação já existente no backend.

### Compatibilidade com o monólito

O algoritmo de CPF e a estrutura do JWT foram implementados para manter compatibilidade com o monólito existente.

### Backend desacoplado

As rotas de negócio são resolvidas pelo backend principal via integração `HTTP_PROXY` do próprio API Gateway - a Lambda entra só para autenticar (`ValidatorHandler`) e autorizar (`AuthorizerHandler`), sem duplicar nem interceptar as regras de negócio da aplicação.

---

## 📚 Projeto

Este repositório faz parte da solução **Oficina Mecânica – Tech Challenge Fase 3**, desenvolvida pelo grupo **Grupo-SOAT**.

### Repositório

[Grupo-SOAT/lambda-code](https://github.com/Grupo-SOAT/lambda-code)

---

## 📄 Licença

Este projeto está licenciado sob a licença **MIT**.

Consulte o arquivo [`LICENSE`](./LICENSE) para mais informações.
