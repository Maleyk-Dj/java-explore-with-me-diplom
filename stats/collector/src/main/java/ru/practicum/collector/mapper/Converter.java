package ru.practicum.collector.mapper;


import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.grpc.stats.action.ActionTypeProto;

public class Converter {

    public static ActionTypeAvro mapToActionTypeAvro(ActionTypeProto actionTypeProto) {
        return switch (actionTypeProto) {
            case ActionTypeProto.ACTION_VIEW -> ActionTypeAvro.VIEW;
            case ActionTypeProto.ACTION_REGISTER -> ActionTypeAvro.REGISTER;
            case ActionTypeProto.ACTION_LIKE -> ActionTypeAvro.LIKE;
            case ActionTypeProto.UNRECOGNIZED -> throw new IllegalArgumentException("Нет такого действия");
        };
    }
}