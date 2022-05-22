import AppException from 'App/Exceptions/AppException'
import { ModelPaginatorContract } from '@ioc:Adonis/Lucid/Orm'
import Hoge from 'App/Models/Hoge'
import { IDtoHoge } from 'App/Http/DTOs/IDtoHoge'
import IHogeRepository from 'App/Http/Interface/IHogeRepository'

export class ServiceHoge implements IHogeRepository {
    public async list(qs: Record<string, any>): Promise<ModelPaginatorContract<Hoge>> {
        const page = Number(qs.page || 1)
        const limit = Number(qs.limit || 20)
        return await Hoge.query().preload('locales').paginate(page, limit)
    }

    public async store(dto: IDtoHoge): Promise<Hoge> {
        const { locales, ...data } = dto
        try {
            const hoge = await Hoge.create(data)
            await hoge.related('locales').updateOrCreateMany(locales, 'code')
            return hoge
        } catch (err) {
            throw new AppException('Hoge store is faild.', err)
        }
    }

    public async update(hoge: Hoge, dto: IDtoHoge): Promise<Hoge> {
        const { locales, ...data } = dto
        try {
            await hoge.related('locales').updateOrCreateMany(locales, 'code')
            return await hoge.merge(data).save()
        } catch (err) {
            throw new AppException('Hoge update is faild.', err)
        }
    }

    public async delete(hoge: Hoge): Promise<void> {
        try {
            return hoge.delete()
        } catch (err) {
            throw new AppException('Hoge not found.', err)
        }
    }
}

const serviceHoge = new ServiceHoge()
export default serviceHoge
