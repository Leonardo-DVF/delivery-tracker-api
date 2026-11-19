# Delivery Tracker API

API de gestão e rastreamento de entregas para portfólio backend Java.
O projeto está sendo desenvolvido em etapas pequenas, com revisão antes de cada commit.

## Etapa atual

Configuração do PostgreSQL, persistência de usuários, cadastro público, login com JWT
e consulta do perfil autenticado, com validação, BCrypt e erros em Problem Details.
O perfil `local` permite criar um operador de desenvolvimento com credenciais externas.
Os endpoints atuais têm documentação interativa OpenAPI/Swagger.
A cotação de frete usa tarifas fictícias por CEP e peso, calculadas pelo backend,
e fica armazenada com validade para travar o preço.
Clientes criam entregas a partir de uma cotação, com código de rastreamento único e
evento inicial; cada cliente consulta só as próprias entregas e operadores consultam todas.
Operadores evoluem o status por uma máquina de estados com histórico de eventos,
clientes cancelam antes da coleta e qualquer pessoa acompanha a entrega pelo código.
Uma transportadora fictícia envia eventos por webhook assinado com HMAC, com deduplicação
por ID do evento. As notificações por e-mail virão na próxima etapa.

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
| `API_DOCS_ENABLED` | `true` | Habilita ou desabilita OpenAPI e Swagger UI juntos |
| `FREIGHT_QUOTE_TTL` | `PT30M` | Validade da cotação em ISO 8601, entre 1 minuto e 24 horas |
| `CARRIER_WEBHOOK_SECRET` | Sem valor predefinido | Segredo HMAC dos webhooks, com pelo menos 32 caracteres; obrigatório |
| `CARRIER_WEBHOOK_TOLERANCE` | `PT5M` | Diferença máxima do timestamp assinado, entre 30 segundos e 15 minutos |

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
As rotas da documentação também permitem consultas por `GET` sem autenticação.
O perfil, a cotação, a criação e as consultas de entregas exigem Bearer JWT;
as demais rotas permanecem bloqueadas nesta etapa.
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

## OpenAPI e Swagger UI

Com a aplicação em execução:

- Swagger UI: http://localhost:8080/swagger-ui.html
- Especificação JSON: http://localhost:8080/v3/api-docs
- Especificação YAML: http://localhost:8080/v3/api-docs.yaml

A documentação usa `springdoc-openapi-starter-webmvc-ui` 3.1.1, compatível com Spring Boot
4.1.x conforme a [matriz oficial](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot).
Sua versão é explícita porque essa biblioteca não é gerenciada pelo BOM do Spring Boot.
As demais dependências continuam usando o gerenciamento de versões do projeto.

O contrato descreve as doze operações atuais, validações, campos aceitos e respostas
de sucesso e erro. Cadastro, login e rastreamento por código são públicos; o webhook da
transportadora é autenticado por assinatura HMAC; perfil, cotação, entregas e eventos
exigem `bearerAuth`. A especificação mostra DTOs e esconde o principal JWT injetado,
campos calculados de validação e detalhes das entidades persistidas.
As senhas são marcadas como `writeOnly` e o formato de erros é `application/problem+json`.
Os exemplos usam dados fictícios e não configuram credenciais na aplicação.

Para testar o fluxo no navegador:

1. Execute `POST /api/v1/auth/register` com seu nome, e-mail e senha.
2. Execute `POST /api/v1/auth/login` com essas credenciais.
3. Copie o campo `accessToken` da resposta.
4. Clique em **Authorize**, cole somente o token, sem o prefixo `Bearer`, e confirme.
5. Execute `GET /api/v1/users/me`.

O Swagger UI não persiste a autorização no armazenamento do navegador entre recargas.
Para desabilitar a especificação e a interface, defina `API_DOCS_ENABLED=false` e
reinicie a aplicação. As rotas da documentação passam a responder `404`.
Esse ajuste não modifica o acesso aos endpoints de negócio ou ao Actuator.
O Actuator continua expondo somente o health, sem detalhes sensíveis.

Os testes abaixo verificam a geração real da especificação, os esquemas, a segurança,
os recursos da interface e a desativação por configuração, sem exigir banco ou Docker:

```powershell
.\mvnw.cmd "-Dtest=OpenApiDocumentationTests,DisabledOpenApiDocumentationTests" test
```

## Cotação de frete fictício

`POST /api/v1/freight/quotes` permite cotar frete para usuários autenticados com
perfil `CUSTOMER` ou `OPERATOR`. A entrada aceita somente `destinationPostalCode`
(string com oito dígitos, preservando zeros iniciais) e `weightKg` (de 0.001 a 1000 kg,
com até três casas decimais). Campos como `amount` e `status` são rejeitados com `400`.

Depois de cadastrar e autenticar o usuário pelos exemplos anteriores:

```powershell
$quote = @{
    destinationPostalCode = "01000000"
    weightKg = 2.000
} | ConvertTo-Json

$quoteResponse = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/freight/quotes" `
    -Headers @{ Authorization = "Bearer $($session.accessToken)" } `
    -ContentType "application/json" -Body $quote
$quoteResponse
```

A resposta `201` contém `quoteId`, `amount: 15.00`, `currency: "BRL"`, o CEP e o peso
cotados, `expiresAt` e um aviso de tarifa fictícia.
Esta demonstração não representa preços reais de transportadoras.
O serviço calcula `preço base + preço por kg × peso` usando `BigDecimal` e arredonda
somente o resultado final para duas casas com `HALF_UP`. Por exemplo, com preço base
10.00 e preço por kg 2.50, 0.002 kg custa 10.01. Dimensões e peso cúbico não entram
na cotação desta versão.

As faixas de CEP incluem ambos os limites. As de peso excluem o limite inferior
e incluem o superior: `(0, 5]` e `(5, 30]` não se sobrepõem.
O perfil `local` carrega estas quatro tarifas de demonstração pelo Flyway:

| CEP inicial | CEP final | Peso em kg | Base (BRL) | Por kg (BRL) |
| --- | --- | --- | --- | --- |
| 00000000 | 59999999 | `(0, 5]` | 10.00 | 2.50 |
| 00000000 | 59999999 | `(5, 30]` | 20.00 | 3.00 |
| 60000000 | 99999999 | `(0, 5]` | 15.00 | 4.00 |
| 60000000 | 99999999 | `(5, 30]` | 30.00 | 5.00 |

Se nenhuma tarifa atender, a API retorna `422` em Problem Details. Assim, pesos acima
de 30 kg são entradas válidas, mas não têm tarifa nas regras de demonstração.
Uma restrição de exclusão GiST no PostgreSQL impede regras com sobreposição simultânea
de CEP e peso, inclusive em gravações concorrentes. Faixas adjacentes são permitidas.
Constraints também impedem faixas inválidas e preços negativos. Defensivamente,
o serviço retorna `409` se encontrar mais de uma regra aplicável.

`V2__create_freight_rates.sql` cria a tabela em todos os perfis. Somente `local`
inclui também `db/local`, com `V3__seed_fictional_freight_rates.sql`. Fora desse perfil,
a tabela começa vazia. Use bancos separados por ambiente: um banco migrado como
`local` mantém a migration V3 no histórico e não deve ser reutilizado em outro perfil.
V3 está reservada aos dados locais; V4 cria entregas e eventos, V5 as cotações e
V6 liga cada entrega à sua cotação. Migrations já commitadas nunca são editadas:
mudanças de schema entram sempre em uma nova versão.

Cada cotação é armazenada em `freight_quotes` com o usuário que a pediu, o CEP, o peso,
o valor e `expires_at`. Ela vale 30 minutos por padrão (`FREIGHT_QUOTE_TTL`) e expira
exatamente em `createdAt + validade`. O valor fica travado: alterar ou remover tarifas
depois não muda cotações já emitidas. Operadores também podem cotar, mas apenas
clientes criam entregas. O horário vem de um `Clock` injetado, o que torna os testes
de expiração determinísticos.

Para executar os testes de cálculo, validação, expiração e autorização sem Docker:

```powershell
.\mvnw.cmd "-Dtest=FreightServiceTests,FreightControllerTests,FreightQuoteTests" test
```

`FreightIntegrationTests` e `FreightQuoteIntegrationTests` verificam limites das faixas,
ausência de tarifa, persistência das cotações e constraints em PostgreSQL real via
Testcontainers. Com Docker Desktop em execução:

```powershell
.\mvnw.cmd "-Dtest=FreightIntegrationTests,FreightQuoteIntegrationTests" test
```

## Criação de entregas

`POST /api/v1/shipments` está disponível somente para `CUSTOMER`. O proprietário é
obtido do `sub` do JWT e confirmado no banco, independentemente dos nomes dos contatos.
Operadores recebem `403`; tokens ausentes ou usuários removidos recebem `401`.
O JSON aceita somente `quoteId`, origem, destino e dimensões. Peso e frete vêm da
cotação. Informar proprietário, peso, preço, status ou código de rastreamento gera `400`.

Na mesma sessão PowerShell dos exemplos de cadastro, login e cotação:

```powershell
$shipmentRequest = @{
    quoteId = $quoteResponse.quoteId
    origin = @{
        contactName = "Sender"
        postalCode = "20000000"
        street = "Origin street"
        number = "10"
        neighborhood = "Center"
        city = "Rio de Janeiro"
        state = "RJ"
    }
    destination = @{
        contactName = "Recipient"
        postalCode = "01000000"
        street = "Destination street"
        number = "20"
        complement = "Apt 1"
        neighborhood = "Center"
        city = "Sao Paulo"
        state = "SP"
    }
    lengthCm = 30
    widthCm = 20
    heightCm = 10
} | ConvertTo-Json -Depth 4

$shipment = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/shipments" `
    -Headers @{ Authorization = "Bearer $($session.accessToken)" } `
    -ContentType "application/json" -Body $shipmentRequest
$shipment
```

A resposta `201` traz o header `Location` com `/api/v1/shipments/{id}` e contém UUID,
código de rastreamento, `quoteId`, endereços, medidas, frete, status `CREATED` e
instantes de criação e atualização em ISO 8601. Neste exemplo, o frete é 15.00 BRL.
O contato da origem representa o remetente e o do destino representa o destinatário,
sem exigir telefone ou documento.

A cotação é validada nesta ordem:

| Situação | Resposta |
| --- | --- |
| Cotação inexistente ou de outro usuário | `404` |
| Cotação expirada | `422` |
| CEP de destino diferente do cotado | `422` |
| Cotação já usada por outra entrega | `409` |

Uma cotação de outro usuário responde `404`, e não `403`, para não revelar que o
identificador existe. O uso único é garantido pela constraint `uq_shipments_quote`.
A verificação prévia no serviço só produz uma mensagem amigável; em requisições
simultâneas com a mesma cotação, o banco aceita uma e rejeita as demais com `409`.

Os dois endereços exigem nome do contato, CEP de oito dígitos, logradouro, número,
bairro, cidade e UF brasileira em maiúsculas. Complemento é opcional. Nome, bairro e
cidade aceitam até 100 caracteres, logradouro 150, número 20 e complemento 100.
Espaços nas extremidades dos textos são removidos ao armazenar os endereços.
Cada dimensão aceita 0.01 a 1000 cm com até duas casas. Não há cálculo de peso cúbico.

Uma transação salva a entrega e seu evento inicial `CREATED`, com origem `API`,
sem localização e com instantes de ocorrência e recebimento iguais ao da criação.
Falhas ao persistir o evento também revertem a entrega. Os status aceitos pelo banco
são `CREATED`, `PICKED_UP`, `IN_TRANSIT`, `OUT_FOR_DELIVERY`, `DELIVERED` e `CANCELED`;
as transições estão descritas em [Ciclo de vida e rastreamento](#ciclo-de-vida-e-rastreamento).

O código de rastreamento usa um UUID aleatório independente (122 bits aleatórios),
representado por 32 caracteres hexadecimais maiúsculos, com unicidade garantida no
PostgreSQL. Uma colisão gera `409` e reverte a transação; não há tentativa automática.
A entidade possui `@Version`, usado nas mudanças de status concorrentes.

## Consulta de entregas

`GET /api/v1/shipments` e `GET /api/v1/shipments/{id}` estão disponíveis para
`CUSTOMER` e `OPERATOR`. O escopo é decidido pelo perfil gravado no banco, nunca por
parâmetros da requisição: clientes veem apenas as próprias entregas e operadores
veem todas. A entrega de outro cliente responde `404`, como se não existisse.

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/shipments?status=CREATED&page=0&size=20" `
    -Headers @{ Authorization = "Bearer $($session.accessToken)" }

Invoke-RestMethod -Uri "http://localhost:8080/api/v1/shipments/$($shipment.id)" `
    -Headers @{ Authorization = "Bearer $($session.accessToken)" }
```

A listagem aceita `status` (opcional), `page` (a partir de 0, padrão 0) e `size`
(1 a 100, padrão 20). Valores fora desses limites, status desconhecidos e UUIDs
malformados retornam `400` em Problem Details. A resposta tem o formato
`{ content, page, size, totalElements, totalPages }`, um DTO próprio em vez da
serialização interna de `Page` do Spring Data, que não é um contrato estável.

A ordenação é fixa: mais recentes primeiro e, em caso de empate no instante de criação,
por `id`. O desempate impede que um item apareça em duas páginas ou desapareça.
A ordem coincide com os índices `(customer_id, created_at DESC, id)` e
`(status, created_at DESC, id)` da V4. Ordenação livre não é aceita, para evitar
consultas sem índice e não expor nomes internos da entidade.

Mudanças de status, cancelamento e consulta pública estão em
[Ciclo de vida e rastreamento](#ciclo-de-vida-e-rastreamento).
O envio de e-mail será incluído na etapa de notificações.

Para verificar criação, consultas, validação, autorização e regras do serviço sem Docker:

```powershell
.\mvnw.cmd "-Dtest=ShipmentServiceTests,ShipmentControllerTests,OpenApiDocumentationTests,DisabledOpenApiDocumentationTests" test
```

Com Docker em execução, verifique também persistência, preço travado, isolamento entre
clientes, paginação e o uso único da cotação sob concorrência:

```powershell
.\mvnw.cmd "-Dtest=ShipmentIntegrationTests,ShipmentQueryIntegrationTests,ShipmentQuoteConcurrencyIntegrationTests" test
```

## Ciclo de vida e rastreamento

O status de uma entrega segue uma máquina de estados:

```
CREATED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED
   └──────────┴────────────┴──→ CANCELED
```

Só é possível avançar um passo por vez. `CANCELED` é aceito até `IN_TRANSIT`;
`DELIVERED` e `CANCELED` são finais. A regra fica em `ShipmentStatus.canTransitionTo`,
com um `switch` exaustivo: um status novo não compila até ter suas transições definidas.
`Shipment.transitionTo` é a única forma de mudar o status, então qualquer caminho
(API e webhooks) obedece à mesma regra. Uma transição inválida responde `409`
com `currentStatus` e `requestedStatus` no Problem Details.

| Método | Rota | Quem | Efeito |
| --- | --- | --- | --- |
| `POST` | `/api/v1/shipments/{id}/events` | `OPERATOR` | Muda o status e registra um evento |
| `POST` | `/api/v1/shipments/{id}/cancel` | `CUSTOMER` (própria) ou `OPERATOR` | Cancela e registra um evento |
| `GET` | `/api/v1/shipments/{id}/events` | Mesmo escopo de `GET /{id}` | Histórico, na ordem em que os eventos foram aplicados |
| `GET` | `/api/v1/tracking/{trackingCode}` | Público | Status, cidade/UF de destino e linha do tempo |

No exemplo, `$operatorSession` é a resposta do login de um operador, criado como em
[Operador no ambiente local](#operador-no-ambiente-local), e `$shipment` é a entrega criada antes.

```powershell
$event = @{ status = "PICKED_UP"; location = "Centro de distribuição Rio" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/shipments/$($shipment.id)/events" `
    -Headers @{ Authorization = "Bearer $($operatorSession.accessToken)" } `
    -ContentType "application/json" -Body $event

Invoke-RestMethod -Uri "http://localhost:8080/api/v1/tracking/$($shipment.trackingCode)"
```

O evento aceita apenas `status` e `location` (opcional, até 150 caracteres; vazio vira
`null`). Os instantes de ocorrência e recebimento são os do servidor, e a origem é
`OPERATOR`. Instantes informados pela transportadora, com eventos fora de ordem, chegam
pelos [webhooks](#webhooks-da-transportadora); por isso a tabela separa `occurred_at` e `received_at`.
A mudança de status e o evento são gravados na mesma transação.

Cancelamento: o cliente cancela a própria entrega somente enquanto ela está `CREATED`,
antes da coleta; depois disso recebe `409` e precisa falar com um operador.
O operador pode cancelar qualquer entrega que a máquina de estados permita.
A regra do cliente é uma política de negócio, separada da máquina de estados, e fica
em `ShipmentService.cancel`. O evento registra a origem `CUSTOMER` ou `OPERATOR`.

Concorrência: a coluna `version` (`@Version`) aplica bloqueio otimista. Se dois
operadores mudam a mesma entrega ao mesmo tempo, apenas um grava; o outro recebe `409`
e pode recarregar e tentar de novo. Não há bloqueio de linha durante a requisição.
O serviço faz `flush` antes de inserir o evento, para que o conflito apareça antes.

Rastreamento público: a rota não exige token e aceita o código em maiúsculas ou
minúsculas. Códigos fora do formato de 32 caracteres hexadecimais respondem `400`
sem consultar o banco; códigos inexistentes, `404`. A resposta é um DTO próprio, sem
ids, nomes, endereços, CEPs, valores ou a origem interna dos eventos, para que dados
pessoais não vazem por acidente. O código tem 122 bits aleatórios, o que torna
inviável descobrir códigos válidos por tentativa. Limite de requisições não faz parte
desta etapa.

Esta etapa não exigiu migration: a V6 já alinhou as constraints de status, e a tabela
`tracking_events` veio da V4. O índice do histórico foi trocado pela V8, junto com os webhooks.

Para verificar a máquina de estados, autorização e validação sem Docker:

```powershell
.\mvnw.cmd "-Dtest=ShipmentStatusTests,ShipmentTests,TrackingServiceTests,TrackingControllerTests,ShipmentServiceTests,ShipmentControllerTests" test
```

Com Docker em execução, verifique o ciclo completo, o cancelamento, o rastreamento
público via HTTP e a disputa entre mudanças simultâneas:

```powershell
.\mvnw.cmd "-Dtest=TrackingEventIntegrationTests,ShipmentCancellationIntegrationTests,PublicTrackingIntegrationTests,TrackingEventConcurrencyIntegrationTests" test
```

## Webhooks da transportadora

`POST /api/v1/webhooks/carrier` recebe eventos de uma transportadora fictícia. A rota não
usa token JWT: a autenticação é uma assinatura HMAC-SHA256 com um segredo compartilhado.

| Cabeçalho | Conteúdo |
| --- | --- |
| `X-Carrier-Timestamp` | Instante da assinatura em segundos Unix |
| `X-Carrier-Signature` | `sha256=` seguido do HMAC em hexadecimal de `<timestamp>.<corpo bruto>` |

```json
{
  "eventId": "evt_20261119_0001",
  "trackingCode": "3F2A9C1B7D4E4F0A8B6C5D2E1F0A9B8C",
  "status": "PICKED_UP",
  "occurredAt": "2026-11-19T14:00:00Z",
  "location": "Centro de distribuição Rio"
}
```

Validação da assinatura, nesta ordem e antes de qualquer leitura do JSON:

- O corpo é recebido como bytes. A assinatura cobre os bytes exatos enviados;
  desserializar e serializar de novo poderia mudar espaços ou a ordem dos campos.
- O timestamp precisa estar a no máximo 5 minutos do relógio do servidor
  (`CARRIER_WEBHOOK_TOLERANCE`). Assim uma requisição capturada não pode ser reenviada depois.
- A comparação usa `MessageDigest.isEqual`, em tempo constante, para que o tempo de resposta
  não revele quantos bytes coincidiram.
- Qualquer falha responde `401` com a mesma mensagem. O motivo vai apenas para o log.

O segredo vem de `CARRIER_WEBHOOK_SECRET`, com pelo menos 32 caracteres. A aplicação não
inicia sem ele, e o `toString` das propriedades mascara o valor. Para gerar um segredo no PowerShell:

```powershell
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
-join ($bytes | ForEach-Object { $_.ToString("x2") })
```

Transportadoras entregam webhooks pelo menos uma vez e nem sempre em ordem. Por isso,
toda resposta `2xx` significa "pare de reenviar", e o campo `outcome` diz o que aconteceu:

| Situação | Resposta |
| --- | --- |
| Próximo status válido | `200` `APPLIED`: muda o status e grava o evento |
| `eventId` já processado | `200` `DUPLICATE`: nada muda |
| Status já ultrapassado, repetido ou impossível (entrega finalizada, `CANCELED` depois de sair para entrega) | `200` `IGNORED`: não é gravado |
| Status que pula etapas, como `IN_TRANSIT` com a entrega em `CREATED` | `409`: a transportadora reenvia depois |
| Código de rastreamento inexistente | `404` |
| JSON malformado ou campos inválidos | `400` |

A deduplicação usa a coluna `carrier_event_id` com a constraint única
`uq_tracking_events_carrier_event` (V7). A verificação prévia no serviço só evita
trabalho; em entregas simultâneas do mesmo evento, o banco e o `@Version` da entrega
garantem que só uma seja aplicada. As demais recebem `DUPLICATE` ou `409`, e o reenvio
seguinte responde `DUPLICATE`. A V7 também exige que todo evento com origem `CARRIER`
tenha `carrier_event_id`, e somente esses.

O `409` para eventos fora de ordem aproveita o reenvio da própria transportadora: quando o
evento anterior chega, o reenvio passa a ser aplicado. Já um status que nunca poderá ser
aplicado responde `200`, para não gerar reenvios infinitos.

O evento guarda `occurredAt` no relógio da transportadora e `receivedAt` no do servidor.
O histórico é listado pela ordem em que os eventos foram aplicados (`received_at`), com
`occurred_at` como desempate. Ordenar pelo instante informado poderia mostrar um status
antes do anterior quando os relógios divergem. A V8 troca o índice da V4 por
`(shipment_id, received_at, occurred_at)`. `occurredAt` não pode estar mais de 5 minutos no futuro.

Diferente da API pública, o webhook ignora campos desconhecidos (*tolerant reader*):
uma transportadora que adiciona um campo ao payload não deve fazer todas as entregas falharem.
`eventId` aceita até 100 caracteres entre letras, números, `.`, `_`, `:` e `-`.

Para simular a transportadora no PowerShell, com a aplicação em execução e `$shipment`
criado antes:

```powershell
$secret = "o mesmo valor de CARRIER_WEBHOOK_SECRET"
$body = @{
    eventId = "evt-0001"
    trackingCode = $shipment.trackingCode
    status = "PICKED_UP"
    occurredAt = (Get-Date).ToUniversalTime().ToString("o")
    location = "Centro de distribuição Rio"
} | ConvertTo-Json -Compress
$timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds().ToString()
$bodyBytes = [Text.Encoding]::UTF8.GetBytes($body)
$hmac = New-Object System.Security.Cryptography.HMACSHA256 -ArgumentList @(, [Text.Encoding]::UTF8.GetBytes($secret))
$hash = $hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes("$timestamp.$body"))
$signature = "sha256=" + (-join ($hash | ForEach-Object { $_.ToString("x2") }))

Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/webhooks/carrier" `
    -ContentType "application/json" -Body $bodyBytes `
    -Headers @{ "X-Carrier-Timestamp" = $timestamp; "X-Carrier-Signature" = $signature }
```

O corpo é enviado como bytes para que o PowerShell não altere a codificação depois da assinatura.
Rotação de segredo (aceitar dois segredos durante a troca), limite de tamanho do corpo e
várias transportadoras (unicidade por `(carrier, carrier_event_id)`) ficam fora desta etapa.

Para verificar assinatura, configuração, regras de entrega e o contrato HTTP sem Docker:

```powershell
.\mvnw.cmd "-Dtest=WebhookSignatureVerifierTests,CarrierWebhookPropertiesTests,CarrierWebhookServiceTests,CarrierWebhookControllerTests" test
```

Com Docker em execução, verifique as constraints da V7, o fluxo HTTP com HMAC real e as
entregas simultâneas do mesmo evento:

```powershell
.\mvnw.cmd "-Dtest=TrackingEventConstraintIntegrationTests,CarrierWebhookIntegrationTests,CarrierWebhookConcurrencyIntegrationTests" test
```
