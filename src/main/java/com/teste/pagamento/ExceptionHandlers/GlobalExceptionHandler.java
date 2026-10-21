package com.teste.pagamento.ExceptionHandlers;

import com.teste.pagamento.data.CheckoutError;

import com.teste.pagamento.ExceptionHandlers.CheckoutErrorResponse.ErrorButton;
import com.teste.pagamento.ExceptionHandlers.CheckoutErrorResponse.FrontendError;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;





@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CheckoutException.class)
    public ResponseEntity<CheckoutErrorResponse> handleCheckout(CheckoutException ex) {
        CheckoutErrorResponse response = buildResponse(
                ex.getError(),
                ex.getCartId()
        );

        return ResponseEntity
                .status(ex.getError().getStatus())
                .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CheckoutErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String description = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .distinct()
                .collect(Collectors.joining("; "));

        if (description.isBlank()) {
            description = "Verifique os dados informados.";
        }

        CheckoutErrorResponse response = createResponse(
                "BLOCK",
                "VALIDATION_ERROR",
                null,
                "P-1007",
                "Dados inválidos",
                description,
                new ErrorButton("Corrigir dados", "/checkout/pagamento"),
                null
        );

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CheckoutErrorResponse> handleInvalidJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(
                buildResponse(CheckoutError.INVALID_REQUEST, null)
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<CheckoutErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest().body(
                buildResponse(CheckoutError.INVALID_REQUEST, null)
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CheckoutErrorResponse> handleUnexpected(Exception ex) {
        // Registrar a exceção completa no logger da aplicação.
        // Nunca devolver o stack trace ao cliente.
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildResponse(CheckoutError.INTERNAL_ERROR, null));
    }

    private CheckoutErrorResponse buildResponse(CheckoutError error, String cartId) {
        return switch (error) {
            case PRODUCT_NOT_FOUND -> createResponse(
                    "BLOCK", error.name(), cartId, "P-1002",
                    "Produto não encontrado",
                    "O produto solicitado não existe ou não está disponível.",
                    new ErrorButton("Voltar ao pagamento", "/checkout/pagamento"),
                    null
            );

            case CART_NOT_FOUND -> createResponse(
                    "BLOCK", error.name(), cartId, "P-1003",
                    "Carrinho não encontrado",
                    "O carrinho não existe ou expirou. Inicie um novo checkout.",
                    new ErrorButton("Iniciar novamente", "/checkout/pagamento"),
                    null
            );

            case INVALID_PAYMENT_METHOD -> createResponse(
                    "BLOCK", error.name(), cartId, "P-1004",
                    "Meio de pagamento inválido",
                    "O meio de pagamento selecionado não está disponível para este carrinho.",
                    new ErrorButton("Trocar meio de pagamento", "/checkout/pagamento"),
                    null
            );

            case PAYMENT_DECLINED -> createResponse(
                    "BLOCK", error.name(), cartId, "P-1001",
                    "Pagamento não aprovado",
                    "Não foi possível concluir o pagamento. Tente novamente ou escolha outro meio.",
                    new ErrorButton("Tentar novamente", "RETRY"),
                    new ErrorButton("Trocar meio de pagamento", "/checkout/pagamento")
            );

            case PAYMENT_ALREADY_APPROVED -> createResponse(
                    "BLOCK", error.name(), cartId, "P-1005",
                    "Pagamento já realizado",
                    "Este carrinho já possui um pagamento aprovado.",
                    new ErrorButton("Voltar ao pagamento", "/checkout/pagamento"),
                    null
            );

            case INVALID_REQUEST -> createResponse(
                    "BLOCK", error.name(), cartId, "P-1006",
                    "Requisição inválida",
                    "Os dados enviados estão ausentes ou possuem formato inválido.",
                    new ErrorButton("Corrigir dados", "/checkout/pagamento"),
                    null
            );

            case VALIDATION_ERROR -> createResponse(
                    "BLOCK", error.name(), cartId, "P-1007",
                    "Dados inválidos",
                    "Verifique os dados informados e tente novamente.",
                    new ErrorButton("Corrigir dados", "/checkout/pagamento"),
                    null
            );

            case INVALID_CARD_NUMBER -> createResponse(
                    "INTERVENTION", error.name(), cartId, "P-1008",
                    "Número de cartão inválido",
                    "Verifique o número do cartão informado.",
                    new ErrorButton("Corrigir cartão", "/checkout/pagamento"),
                    null
            );

            case INVALID_CARD_EXPIRY -> createResponse(
                    "INTERVENTION", error.name(), cartId, "P-1009",
                    "Validade inválida",
                    "A data de validade do cartão é inválida ou está vencida.",
                    new ErrorButton("Corrigir validade", "/checkout/pagamento"),
                    null
            );

            case INVALID_CARD_CVV -> createResponse(
                    "INTERVENTION", error.name(), cartId, "P-1010",
                    "Código de segurança inválido",
                    "Verifique o CVV informado.",
                    new ErrorButton("Corrigir CVV", "/checkout/pagamento"),
                    null
            );

            case INVALID_CARD_HOLDER -> createResponse(
                    "INTERVENTION", error.name(), cartId, "P-1011",
                    "Nome do titular inválido",
                    "Informe o nome do titular do cartão corretamente.",
                    new ErrorButton("Corrigir nome", "/checkout/pagamento"),
                    null
            );

            case INVALID_PIX_REQUEST -> createResponse(
                    "INTERVENTION", error.name(), cartId, "P-1012",
                    "Dados do Pix inválidos",
                    "A requisição Pix contém informações inválidas.",
                    new ErrorButton("Tentar novamente", "RETRY"),
                    null
            );

            case EMPTY_CART -> createResponse(
                    "BLOCK", error.name(), cartId, "P-1013",
                    "Carrinho vazio",
                    "Adicione pelo menos um produto antes de iniciar o checkout.",
                    new ErrorButton("Voltar ao pagamento", "/checkout/pagamento"),
                    null
            );

            case INVALID_CART_STATE -> createResponse(
                    "BLOCK", error.name(), cartId, "P-1014",
                    "Estado do carrinho inválido",
                    "Este carrinho não pode ser processado no estado atual.",
                    new ErrorButton("Iniciar novamente", "/checkout/pagamento"),
                    null
            );

            case PAYMENT_IN_PROGRESS -> createResponse(
                    "INTERVENTION", error.name(), cartId, "P-1015",
                    "Pagamento em processamento",
                    "Este pagamento já está sendo processado. Aguarde a conclusão.",
                    new ErrorButton("Verificar novamente", "RETRY"),
                    null
            );

            case DUPLICATE_REQUEST -> createResponse(
                    "BLOCK", error.name(), cartId, "P-1016",
                    "Requisição duplicada",
                    "Esta operação já foi enviada e não pode ser repetida.",
                    new ErrorButton("Voltar ao pagamento", "/checkout/pagamento"),
                    null
            );

            case PRODUCT_SERVICE_UNAVAILABLE -> createResponse(
                    "BLOCK", error.name(), cartId, "P-1017",
                    "Produtos indisponíveis",
                    "Não foi possível consultar os produtos neste momento.",
                    new ErrorButton("Tentar novamente", "RETRY"),
                    null
            );

            case PAYMENT_METHOD_SERVICE_UNAVAILABLE -> createResponse(
                    "BLOCK", error.name(), cartId, "P-1018",
                    "Meios de pagamento indisponíveis",
                    "Não foi possível consultar os meios de pagamento.",
                    new ErrorButton("Tentar novamente", "RETRY"),
                    null
            );

            case REDIS_UNAVAILABLE -> createResponse(
                    "BLOCK", error.name(), cartId, "P-1019",
                    "Serviço temporariamente indisponível",
                    "Não foi possível recuperar as informações do checkout.",
                    new ErrorButton("Tentar novamente", "RETRY"),
                    null
            );

            case DATABASE_UNAVAILABLE -> createResponse(
                    "BLOCK", error.name(), cartId, "P-1020",
                    "Serviço temporariamente indisponível",
                    "Não foi possível processar sua solicitação neste momento.",
                    new ErrorButton("Tentar novamente", "RETRY"),
                    null
            );

            case MESSAGE_BROKER_UNAVAILABLE -> createResponse(
                    "BLOCK", error.name(), cartId, "P-1021",
                    "Processamento indisponível",
                    "Não foi possível iniciar o processamento da operação.",
                    new ErrorButton("Tentar novamente", "RETRY"),
                    null
            );

            case EXTERNAL_SERVICE_TIMEOUT -> createResponse(
                    "BLOCK", error.name(), cartId, "P-1022",
                    "Tempo de resposta excedido",
                    "O serviço demorou mais que o esperado para responder.",
                    new ErrorButton("Tentar novamente", "RETRY"),
                    null
            );

            case INTERNAL_ERROR -> createResponse(
                    "BLOCK", error.name(), cartId, "P-1023",
                    "Erro interno",
                    "Ocorreu um problema inesperado. Tente novamente mais tarde.",
                    new ErrorButton("Voltar ao pagamento", "/checkout/pagamento"),
                    null
            );
        };
    }

    private CheckoutErrorResponse createResponse(
            String displayType,
            String errorCode,
            String cartId,
            String code,
            String title,
            String description,
            ErrorButton primaryButton,
            ErrorButton secondaryButton
    ) {
        return new CheckoutErrorResponse(
                displayType,
                errorCode,
                cartId,
                new FrontendError(
                        code,
                        title,
                        description,
                        primaryButton,
                        secondaryButton
                )
        );
    }
}