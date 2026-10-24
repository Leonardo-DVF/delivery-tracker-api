# Delivery Tracker API

API de gestão e rastreamento de entregas para portfólio backend Java.
O projeto está sendo desenvolvido em etapas pequenas, com revisão antes de cada commit.

## Etapa atual

Configuração do PostgreSQL, persistência de usuários e cadastro público de clientes,
com validação, senha em BCrypt e erros em Problem Details.
O login, a emissão de JWT e os demais endpoints serão implementados nas próximas etapas.

## Tecnologias

- Java 21 e Maven Wrapper.
- Spring Boot 4.1.1, Spring Web MVC, Validation e Spring Security.
- Spring Data JPA, PostgreSQL 17 e Flyway.
- Testcontainers com PostgreSQL para testes de integração.

## Preparar o ambiente local

Instale Java 21 e Docker Desktop com suporte a containers Linux.
O Maven Wrapper baixa o Maven automaticamente; não é necessário instalá-lo.

Na raiz do projeto, usando PowerShell:

```powershell
Copy-Item .env.example .env
```

Edite o arquivo `.env` e substitua o valor de `DB_PASSWORD` por uma senha local.
Use valores simples no formato `CHAVE=valor`, sem aspas nem instruções `export`.
Uma senha hexadecimal evita diferenças de interpretação entre Compose e Java Properties.
O arquivo `.env` é ignorado pelo Git.

Inicie o Docker Desktop e execute:

```powershell
docker compose up -d --wait postgres
docker compose ps
```

O banco fica acessível apenas pela máquina local. Seus dados são persistidos no volume
`postgres-data`, inclusive depois de executar `docker compose down`.
As credenciais de inicialização só são aplicadas quando o volume está vazio;
alterar a senha em `.env` não altera automaticamente a senha de um banco já criado.

## Executar a aplicação

Na raiz do projeto:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

O perfil `local` importa `.env` como Java Properties. Variáveis de ambiente do processo
têm precedência sobre esse arquivo. Nos demais perfis, forneça as variáveis de ambiente
diretamente. Nesta etapa, o Compose executa somente o PostgreSQL; a aplicação roda pelo Maven.

| Variável | Padrão | Uso |
| --- | --- | --- |
| `DB_HOST` | `localhost` | Host usado pela aplicação |
| `DB_PORT` | `5432` | Porta local do PostgreSQL |
| `DB_NAME` | `delivery_tracker` | Nome do banco |
| `DB_USERNAME` | `delivery` | Usuário do banco |
| `DB_PASSWORD` | Sem senha predefinida | Senha definida localmente |

O JPA valida o schema com `ddl-auto: validate`, sem criar ou modificar tabelas.
O Flyway executa as migrations de `src/main/resources/db/migration` antes da validação do JPA.
O Open Session in View está desativado, e o Hibernate usa UTC para persistir instantes.

## Testes

Com Docker Desktop em execução:

```powershell
.\mvnw.cmd test
```

O teste de contexto usa um PostgreSQL 17 isolado via Testcontainers.
Os testes não dependem do banco do Compose nem exigem um arquivo `.env`.
No Linux ou macOS, utilize `./mvnw` no lugar de `.\mvnw.cmd`.

## Persistência de usuários

O módulo `user` contém a entidade `User`, o enum `UserRole` e o `UserRepository`.
A migration `V1__create_users.sql` cria a tabela `app_users` com:

- UUID gerado pela aplicação.
- Nome, e-mail, hash da senha, perfil e instante de criação.
- E-mail único, sem espaços nas extremidades e em letras minúsculas.
- Perfis `CUSTOMER` e `OPERATOR`, armazenados como texto e limitados por constraint.
- Instante de criação em `TIMESTAMP WITH TIME ZONE`, mapeado para `Instant`.

A entidade normaliza o e-mail usando `Locale.ROOT`. O banco também exige a forma
normalizada e a unicidade, inclusive em inserções que não passam pelo código Java.
A busca no repositório recebe o e-mail já normalizado.

O campo `passwordHash` recebe um hash BCrypt calculado pelo serviço de cadastro.
O cadastro público define sempre o perfil `CUSTOMER`.

Os testes de integração verificam persistência dos perfis, busca por e-mail,
unicidade após normalização e rejeição de e-mails não normalizados e perfis inválidos
por inserção direta no PostgreSQL. As transações dos testes são revertidas após cada caso.

## Cadastro público

`POST /api/v1/auth/register` aceita apenas `name`, `email` e `password`.
O nome é obrigatório e limitado a 100 caracteres. O e-mail deve ser válido, com até
254 caracteres; nome e e-mail têm espaços nas extremidades removidos antes da validação.
A senha deve ter de 12 a 72 caracteres e no máximo 72 bytes em UTF-8.
Ela é preservada como informada, incluindo espaços, e armazenada com BCrypt de custo 12.

Exemplo no PowerShell, com a aplicação em execução:

```powershell
$request = @{
    name = "Customer"
    email = "customer@example.test"
    password = "example-password-2026"
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/auth/register" `
    -ContentType "application/json" -Body $request
```

A resposta `201 Created` contém `id`, `name`, `email`, `role` e `createdAt`;
nunca contém senha ou hash. O campo `role` não faz parte da entrada:
enviá-lo, mesmo com `CUSTOMER`, gera `400 Bad Request`.
Outros campos desconhecidos também são rejeitados.

Entradas inválidas retornam `400`; e-mails já cadastrados retornam `409 Conflict`.
Os erros usam `application/problem+json`, com `type`, `title`, `status`, `detail`
e, em falhas de validação, a lista `errors` com campo e mensagem, sem o valor da senha.
A constraint única do banco também protege contra cadastros concorrentes:
um conflito na persistência retorna `409`, sem detalhes internos do banco.

A configuração de segurança libera somente o cadastro por `POST` e o health por `GET`.
As demais rotas permanecem bloqueadas nesta etapa. Não há sessão, formulário de login
ou autenticação Basic. O endpoint `/actuator/health` é público sem detalhes sensíveis;
nenhum outro endpoint do Actuator é exposto.
O CSRF está desativado porque a API não autentica por cookies de sessão;
a autenticação por Bearer JWT será implementada na próxima etapa.

Para executar apenas os testes de serviço e de HTTP, sem Docker:

```powershell
.\mvnw.cmd "-Dtest=RegistrationServiceTests,RegistrationControllerTests" test
```

A suíte completa também inclui `RegistrationIntegrationTests`, com a cadeia de
segurança, validação, serviço e persistência reais usando PostgreSQL via Testcontainers.
