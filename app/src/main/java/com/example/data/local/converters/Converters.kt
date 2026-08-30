package com.example.data.local.converters

import androidx.room.TypeConverter
import com.example.domain.model.DeliveryStatus
import com.example.domain.model.EndConditionType
import com.example.domain.model.LeapYearHandling
import com.example.domain.model.MessageChannel
import com.example.domain.model.RecurrenceType
import com.example.domain.model.RetryPolicy
import com.example.domain.model.ScheduleStatus
import com.example.domain.model.ShortMonthHandling
import com.example.domain.model.TemplateCategory

class Converters {

    @TypeConverter
    fun fromMessageChannel(value: MessageChannel): String = value.name

    @TypeConverter
    fun toMessageChannel(value: String): MessageChannel = MessageChannel.valueOf(value)

    @TypeConverter
    fun fromScheduleStatus(value: ScheduleStatus): String = value.name

    @TypeConverter
    fun toScheduleStatus(value: String): ScheduleStatus = ScheduleStatus.valueOf(value)

    @TypeConverter
    fun fromRecurrenceType(value: RecurrenceType): String = value.name

    @TypeConverter
    fun toRecurrenceType(value: String): RecurrenceType = RecurrenceType.valueOf(value)

    @TypeConverter
    fun fromEndConditionType(value: EndConditionType): String = value.name

    @TypeConverter
    fun toEndConditionType(value: String): EndConditionType = EndConditionType.valueOf(value)

    @TypeConverter
    fun fromShortMonthHandling(value: ShortMonthHandling): String = value.name

    @TypeConverter
    fun toShortMonthHandling(value: String): ShortMonthHandling = ShortMonthHandling.valueOf(value)

    @TypeConverter
    fun fromLeapYearHandling(value: LeapYearHandling): String = value.name

    @TypeConverter
    fun toLeapYearHandling(value: String): LeapYearHandling = LeapYearHandling.valueOf(value)

    @TypeConverter
    fun fromRetryPolicy(value: RetryPolicy): String = value.name

    @TypeConverter
    fun toRetryPolicy(value: String): RetryPolicy = RetryPolicy.valueOf(value)

    @TypeConverter
    fun fromDeliveryStatus(value: DeliveryStatus): String = value.name

    @TypeConverter
    fun toDeliveryStatus(value: String): DeliveryStatus = DeliveryStatus.valueOf(value)

    @TypeConverter
    fun fromTemplateCategory(value: TemplateCategory): String = value.name

    @TypeConverter
    fun toTemplateCategory(value: String): TemplateCategory = TemplateCategory.valueOf(value)

    @TypeConverter
    fun fromIntList(list: List<Int>?): String = list?.joinToString(",") ?: ""

    @TypeConverter
    fun toIntList(data: String?): List<Int> {
        if (data.isNullOrBlank()) return emptyList()
        return data.split(",").mapNotNull { it.trim().toIntOrNull() }
    }
}
