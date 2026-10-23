# Delivery Tracker API

API de gestão e rastreamento de entregas para portfólio backend Java.
O projeto está sendo desenvolvido em etapas pequenas, com revisão antes de cada commit.

## Etapa atual

Estrutura inicial do Spring Boot e configuração do PostgreSQL para desenvolvimento.
Os endpoints e as regras de negócio serão implementados nas próximas etapas.

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
O Flyway já está nas dependências e receberá migrations junto aos primeiros domínios.
O Open Session in View está desativado, e o Hibernate usa UTC para persistir instantes.

## Testes

Com Docker Desktop em execução:

```powershell
.\mvnw.cmd test
```

O teste de contexto usa um PostgreSQL 17 isolado via Testcontainers.
Ele não depende do banco do Compose nem exige um arquivo `.env`.
No Linux ou macOS, utilize `./mvnw` no lugar de `.\mvnw.cmd`.
