# Delivery Tracker API

API de gestão e rastreamento de entregas para portfólio backend Java.
O projeto está sendo desenvolvido em etapas pequenas, com revisão antes de cada commit.

## Etapa atual

Configuração do PostgreSQL, persistência de usuários, cadastro público, login com JWT
e consulta do perfil autenticado, com validação, BCrypt e erros em Problem Details.
O perfil `local` permite criar um operador de desenvolvimento com credenciais externas.
Entregas, frete, rastreamento e integrações serão implementados nas próximas etapas.

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

Gere as chaves RSA de desenvolvimento, uma única vez, na raiz do projeto:

```powershell
java tools/GenerateJwtKeys.java
```

O gerador usa apenas o Java 21 e cria um par RSA de 3072 bits em `secrets/`:
`private.pem` em PKCS#8 e `public.pem` em X.509. Ele recusa sobrescrever arquivos
existentes. A pasta é ignorada pelo Git. No Windows, os arquivos herdam as permissões
da pasta; no Linux/macOS, a chave privada é criada com permissão `600`.

Depois execute, na raiz do projeto:

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
| `JWT_ISSUER` | `delivery-tracker-api` | Emissor esperado e usado no JWT |
| `JWT_PRIVATE_KEY` | `file:./secrets/private.pem` | Chave privada RSA em PEM PKCS#8 |
| `JWT_PUBLIC_KEY` | `file:./secrets/public.pem` | Chave pública RSA em PEM X.509 |
| `JWT_TTL_SECONDS` | `3600` | Validade do token, entre 60 e 86400 segundos |
| `LOCAL_OPERATOR_EMAIL` | Não definido | E-mail do operador; usado somente no perfil `local` |
| `LOCAL_OPERATOR_PASSWORD` | Não definida | Senha do operador; usada somente no perfil `local` |

As chaves são obrigatórias ao iniciar a aplicação e precisam formar um par RSA de
pelo menos 2048 bits. Em outros ambientes, forneça caminhos externos pelas variáveis
`JWT_PRIVATE_KEY` e `JWT_PUBLIC_KEY`. Nenhuma chave ou senha real faz parte do repositório.

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
As chaves de teste são geradas temporariamente, sem depender de `secrets/`.
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

A configuração de segurança libera o cadastro e o login por `POST` e o health por `GET`.
O perfil exige Bearer JWT; as demais rotas permanecem bloqueadas nesta etapa.
Não há sessão, formulário de login
ou autenticação Basic. O endpoint `/actuator/health` é público sem detalhes sensíveis;
nenhum outro endpoint do Actuator é exposto.
O CSRF está desativado porque a API não autentica por cookies de sessão;
a autenticação usa Bearer JWT no cabeçalho `Authorization`.

Para executar apenas os testes de serviço e de HTTP, sem Docker:

```powershell
.\mvnw.cmd "-Dtest=RegistrationServiceTests,RegistrationControllerTests" test
```

A suíte completa também inclui `RegistrationIntegrationTests`, com a cadeia de
segurança, validação, serviço e persistência reais usando PostgreSQL via Testcontainers.

## Login e perfil autenticado

`POST /api/v1/auth/login` recebe apenas `email` e `password`. O e-mail é normalizado
como no cadastro. A senha é comparada com o hash BCrypt e nunca é devolvida.
E-mail desconhecido e senha incorreta produzem o mesmo `401 Unauthorized`, com a
mensagem `Invalid email or password`. A comparação BCrypt também é executada quando
o e-mail não existe, usando um hash fictício, para reduzir diferenças de tempo.

Após cadastrar um cliente pelo exemplo anterior:

```powershell
$credentials = @{
    email = "customer@example.test"
    password = "example-password-2026"
} | ConvertTo-Json

$session = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/auth/login" `
    -ContentType "application/json" -Body $credentials

Invoke-RestMethod -Uri "http://localhost:8080/api/v1/users/me" `
    -Headers @{ Authorization = "Bearer $($session.accessToken)" }
```

O login retorna `accessToken`, `tokenType` (`Bearer`), `expiresIn` em segundos e
`expiresAt` em ISO 8601. O JWT é assinado com RS256 e contém somente `iss`, `sub`
(UUID do usuário), `iat`, `exp` e `role`. E-mail, nome, senha e hash não são incluídos.

O Resource Server valida a assinatura, RS256, issuer, expiração, `nbf` quando presente,
UUID e perfil. `iat` e `exp` são obrigatórios; tokens com emissão futura são rejeitados.
Não há tolerância adicional de relógio: mantenha os relógios sincronizados.
O perfil é convertido em uma autoridade `ROLE_CUSTOMER` ou `ROLE_OPERATOR`.
As chaves são lidas de arquivos locais configurados, sem descoberta de emissor pela rede.

`GET /api/v1/users/me` devolve apenas o perfil correspondente ao `sub` validado,
sem receber um ID de usuário na URL ou no payload. Ambos os perfis podem acessá-lo.
Token ausente, inválido ou expirado gera `401`. Um usuário removido do banco também
recebe `401`. O DTO possui os mesmos cinco campos públicos retornados no cadastro.

Nesta versão, não há refresh token, revogação individual, logout no servidor ou
rotação simultânea de múltiplas chaves. Tokens permanecem válidos até expirar;
trocar o par de chaves e reiniciar a aplicação invalida tokens assinados pelo par antigo.
A criação de operadores para desenvolvimento está disponível somente no perfil `local`.

Para verificar autenticação sem Docker, com assinaturas e filtros reais e repositório simulado:

```powershell
.\mvnw.cmd "-Dtest=RegistrationServiceTests,RegistrationControllerTests,AuthenticationControllerTests,JwtConfigTests" test
```

`AuthenticationIntegrationTests` cobre cadastro, login e consulta do perfil com o
PostgreSQL real do Testcontainers. Execute `.\mvnw.cmd test` com Docker para a suíte completa.

## Operador no ambiente local

Para criar um operador, defina `LOCAL_OPERATOR_EMAIL` e `LOCAL_OPERATOR_PASSWORD` no
ambiente do processo ou no arquivo `.env` local. As duas linhas opcionais estão
comentadas em `.env.example`; descomente-as somente no seu `.env` e escolha uma senha.
Não existe senha padrão nem endpoint público para criar ou promover operadores.

Inicie a aplicação com o perfil `local` pelo comando de execução documentado acima.
Na inicialização, o `LocalOperatorBootstrap` executa uma transação que:

- Não faz alterações quando as duas variáveis estão ausentes ou vazias.
- Valida o e-mail e a senha com as mesmas regras usadas no cadastro.
- Normaliza o e-mail e cria o usuário com perfil `OPERATOR` e hash BCrypt de custo 12.
- Preserva um operador que já tenha o e-mail configurado, inclusive seu nome e hash.
- Interrompe a inicialização se o e-mail pertencer a um cliente, sem promovê-lo.
- Interrompe a inicialização se apenas uma variável for informada ou os dados forem inválidos.

As mensagens de erro não incluem as credenciais fornecidas. Fora do perfil `local`,
esse componente não é registrado, mesmo que as variáveis estejam definidas.
Mudar a senha nas variáveis depois da criação não redefine a senha persistida;
esta etapa não inclui recuperação ou redefinição de senha.
Se duas instâncias locais tentarem criar o mesmo operador simultaneamente, a constraint
única protege o banco; uma delas poderá falhar na inicialização.

Depois da criação, use `POST /api/v1/auth/login` com as credenciais do operador e
consulte `GET /api/v1/users/me` com o token. A resposta do perfil terá `role: OPERATOR`.
O cadastro público continua criando exclusivamente clientes e rejeitando `role` na entrada.

Para verificar as regras e a ativação por perfil sem Docker:

```powershell
.\mvnw.cmd "-Dtest=LocalOperatorBootstrapTests" test
```

`LocalOperatorIntegrationTests` verifica persistência, reinicialização idempotente,
login e consulta do operador no PostgreSQL do Testcontainers, além de proteger clientes
contra promoção. Esses testes exigem Docker e são incluídos em `.\mvnw.cmd test`.
