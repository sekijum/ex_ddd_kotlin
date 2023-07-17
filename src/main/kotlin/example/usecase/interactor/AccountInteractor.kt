package example.usecase.interactor

import example.domain.model.account.*
import example.domain.model.core.DomainEventPublisher
import example.domain.repository.AccountRepository
import example.usecase.ApplicationException
import example.usecase.inputport.AccountUseCase
import example.usecase.inputport.command.account.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono

/**
 * アカウント([Account])ドメインの操作を提供するアプリケーションサービス。
 */
@Service
class AccountInteractor(
    private val accountRepository: AccountRepository,
    private val domainEventPublisher: DomainEventPublisher<AccountEvent<*>>) : AccountUseCase {

    @Transactional(readOnly = true)
    override fun find(command: FindAccountCommand): Mono<Account> = runCatching {
        val accountId = AccountId.valueOf(command.accountId)

        Mono.just(accountRepository.find(accountId))
            .onErrorResume { Mono.error(it.error()) }
    }
        .getOrElse { Mono.error(it.error()) }

    @Transactional(readOnly = true)
    override fun findAll(command: FindAllAccountCommand): Mono<Pair<Int, List<Account>>> = runCatching {
        Mono.just(accountRepository.count() to accountRepository.findAll(command.limit, command.offset))
    }
        .getOrElse { Mono.error(it.error()) }

    @Transactional(timeout = TRANSACTIONAL_TIMEOUT_SECONDS, rollbackFor = [Exception::class])
    override fun create(command: CreateAccountCommand): Mono<Account> = runCatching {
        val accountId = accountRepository.nextAccountId()
        val name = Name.valueOf(command.name)
        val namePronunciation = NamePronunciation.valueOf(command.namePronunciation)
        val email = Email.valueOf(command.email)
        val password = Password.valueOf(command.password, accountId)

        val created = Account
            .create(accountId, name, namePronunciation, email, password)
            .also { accountRepository.add(it) }

        Mono.just(created)
            .also { created.publish() }
            .onErrorResume { Mono.error(it.error()) }
    }
        .getOrElse { Mono.error(it.error()) }

    @Transactional(timeout = TRANSACTIONAL_TIMEOUT_SECONDS, rollbackFor = [Exception::class])
    override fun update(command: UpdateAccountCommand): Mono<Account> = runCatching {
        val accountId = AccountId.valueOf(command.accountId)
        val name = command.name?.let { Name.valueOf(it) }
        val namePronunciation = command.namePronunciation?.let { NamePronunciation.valueOf(it) }
        val email = command.email?.let { Email.valueOf(it) }
        val password = command.password?.let { Password.valueOf(it, accountId) }

        val updated = accountRepository
            .find(accountId, lock = true)
            .update(name, namePronunciation, email, password)
            .also { accountRepository.set(it) }

        Mono.just(updated)
            .also { updated.publish() }
            .onErrorResume { Mono.error(it.error()) }
    }
        .getOrElse { Mono.error(it.error()) }

    @Transactional(timeout = TRANSACTIONAL_TIMEOUT_SECONDS, rollbackFor = [Exception::class])
    override fun delete(command: DeleteAccountCommand): Mono<Account> = runCatching {
        val accountId = AccountId.valueOf(command.accountId)

        val deleted = accountRepository
            .find(accountId, lock = true)
            .delete()
            .also { accountRepository.remove(it) }

        Mono.just(deleted)
            .also { deleted.publish() }
            .onErrorResume { Mono.error(it.error()) }
    }
        .getOrElse { Mono.error(it.error()) }

    private fun Account.publish() {
        occurredEvents().forEach { domainEventPublisher.publish(it) }
    }

    private fun Throwable.error(): Throwable =
        when (this) {
            is AccountInvalidRequestException -> ApplicationException(type, 400, message, this)
            is AccountNotFoundException -> ApplicationException(type, 404, message, this)
            is AccountInvalidDataStateException -> ApplicationException(type, 409, message, this)
            is AccountUpdateFailedException -> ApplicationException(type, 500, message, this)
            else -> ApplicationException(message, this)
        }

    private companion object {

        const val TRANSACTIONAL_TIMEOUT_SECONDS: Int = 10
    }
}
