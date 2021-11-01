/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.rfc8621.contract

import java.net.URL
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

import com.google.common.collect.ImmutableSet
import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import javax.inject.Inject
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.api.model.{DeviceClientId, PushSubscription, PushSubscriptionCreationRequest, PushSubscriptionId, PushSubscriptionServerURL, TypeName}
import org.apache.james.jmap.api.pushsubscription.PushSubscriptionRepository
import org.apache.james.jmap.change.{EmailDeliveryTypeName, EmailTypeName, MailboxTypeName}
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UTCDate
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.PushSubscriptionSetMethodContract.TIME_FORMATTER
import org.apache.james.utils.{DataProbeImpl, GuiceProbe}
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.SMono

object PushSubscriptionSetMethodContract {
  val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")
}
class PushSubscriptionProbe @Inject()(pushSubscriptionRepository: PushSubscriptionRepository) extends GuiceProbe {
  def createPushSubscription(username: Username, url: PushSubscriptionServerURL, deviceId: DeviceClientId, types: Seq[TypeName]): PushSubscription =
    SMono(pushSubscriptionRepository.save(username, PushSubscriptionCreationRequest(
      deviceClientId = deviceId,
      url = url,
      types = types)))
      .block()

  def retrievePushSubscription(username: Username, id: PushSubscriptionId): PushSubscription =
    SMono(pushSubscriptionRepository.get(username, ImmutableSet.of(id))).block()
}

class PushSubscriptionProbeModule extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[GuiceProbe])
      .addBinding()
      .to(classOf[PushSubscriptionProbe])
  }
}

trait PushSubscriptionSetMethodContract {


  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()
  }

  @Test
  def setMethodShouldNotRequireAccountId(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": ["Mailbox"]
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
  }

  @Test
  def setMethodShouldFailWhenMissingCapability(): Unit = {
    val request: String =
      """{
        |    "using": [],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": ["Mailbox"]
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "error",
           |            {
           |                "type": "unknownMethod",
           |                "description": "Missing capability(ies): urn:ietf:params:jmap:core"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldNotCreatedWhenMissingTypesPropertyInCreationRequest(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086"
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "PushSubscription/set",
           |            {
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "Missing '/types' property in PushSubscription object"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldNotCreatedWhenTypesPropertyIsEmpty(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": []
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "PushSubscription/set",
           |            {
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "types must not be empty"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldNotCreatedWhenInvalidURLProperty(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "invalid",
        |                  "types": ["Mailbox"]
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "PushSubscription/set",
           |            {
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "'/url' property in PushSubscription object is not valid"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }


  @Test
  def setMethodShouldNotCreatedWhenCreationRequestHasVerificationCodeProperty(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": ["Mailbox"],
        |                  "verificationCode": "abc"
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "PushSubscription/set",
           |            {
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "Some server-set properties were specified",
           |                        "properties": [
           |                            "verificationCode"
           |                        ]
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldNotCreatedWhenCreationRequestHasIdProperty(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": ["Mailbox"],
        |                  "id": "abc"
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "PushSubscription/set",
           |            {
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "Some server-set properties were specified",
           |                        "properties": [
           |                            "id"
           |                        ]
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldNotCreatedWhenInValidExpiresProperty(): Unit = {
    val invalidExpire: String = UTCDate(ZonedDateTime.now().minusDays(1)).asUTC.format(TIME_FORMATTER)
    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [
         |      [
         |        "PushSubscription/set",
         |        {
         |            "create": {
         |                "4f29": {
         |                  "deviceClientId": "a889-ffea-910",
         |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
         |                  "expires": "$invalidExpire",
         |                  "types": ["Mailbox"]
         |                }
         |              }
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "PushSubscription/set",
           |            {
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "`$invalidExpire` expires must be greater than now"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldNotCreatedWhenInValidTypesProperty(): Unit = {
    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [
         |      [
         |        "PushSubscription/set",
         |        {
         |            "create": {
         |                "4f29": {
         |                  "deviceClientId": "a889-ffea-910",
         |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
         |                  "types": ["invalid"]
         |                }
         |              }
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "PushSubscription/set",
           |            {
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "'/types(0)' property in PushSubscription object is not valid: Unknown typeName invalid"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldNotCreatedWhenDeviceClientIdExists(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": ["Mailbox"]
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)

    val response: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "PushSubscription/set",
           |            {
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "`a889-ffea-910` deviceClientId must be unique"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldAcceptValidExpiresProperty(): Unit = {
    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [
         |      [
         |        "PushSubscription/set",
         |        {
         |            "create": {
         |                "4f29": {
         |                  "deviceClientId": "a889-ffea-910",
         |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
         |                  "expires": "${UTCDate(ZonedDateTime.now().plusDays(1)).asUTC.format(TIME_FORMATTER)}",
         |                  "types": ["Mailbox"]
         |                }
         |              }
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

    val response: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "PushSubscription/set",
           |            {
           |                "created": {
           |                    "4f29": {
           |                        "id": "$${json-unit.ignore}",
           |                        "expires": "$${json-unit.ignore}"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldCreatedWhenValidRequest(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": ["Mailbox"]
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "PushSubscription/set",
           |            {
           |                "created": {
           |                    "4f29": {
           |                        "id": "$${json-unit.ignore}",
           |                        "expires": "$${json-unit.ignore}"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldCreatedSeveralValidCreationRequest(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f28": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": ["Mailbox"]
        |                },
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-912",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": ["Email"]
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "PushSubscription/set",
           |            {
           |                "created": {
           |                    "4f28": {
           |                        "id": "$${json-unit.ignore}",
           |                        "expires": "$${json-unit.ignore}"
           |                    },
           |                    "4f29": {
           |                        "id": "$${json-unit.ignore}",
           |                        "expires": "$${json-unit.ignore}"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldSuccessWhenMixCase(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f28": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": ["Mailbox"]
        |                },
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-912",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": ["invalid"]
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "PushSubscription/set",
           |            {
           |                "created": {
           |                    "4f28": {
           |                        "id": "$${json-unit.ignore}",
           |                        "expires": "$${json-unit.ignore}"
           |                    }
           |                },
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "'/types(0)' property in PushSubscription object is not valid: Unknown typeName invalid"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def updateShouldValidateVerificationCode(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[PushSubscriptionProbe])
    val pushSubscription = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URL("https://example.com/push/?device=X8980fc&client=12c6d086")),
        deviceId = DeviceClientId("12c6d086"),
        types = Seq(MailboxTypeName, EmailDeliveryTypeName, EmailTypeName))

    val request: String =
      s"""{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "update": {
        |                "${pushSubscription.id.serialise}": {
        |                  "verificationCode": "${pushSubscription.verificationCode.value}"
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "PushSubscription/set",
           |            {
           |                "updated": {
           |                    "${pushSubscription.id.serialise}": {}
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)

    assertThat(probe.retrievePushSubscription(BOB, pushSubscription.id).validated).isTrue
  }

  @Test
  def updateMixed(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[PushSubscriptionProbe])
    val pushSubscription1 = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URL("https://example.com/push/?device=X8980fc&client=12c6d086")),
        deviceId = DeviceClientId("12c6d081"),
        types = Seq(MailboxTypeName, EmailDeliveryTypeName, EmailTypeName))
    val pushSubscription2 = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URL("https://example.com/push/?device=X8980fc&client=12c6d086")),
        deviceId = DeviceClientId("12c6d082"),
        types = Seq(MailboxTypeName, EmailDeliveryTypeName, EmailTypeName))
    val pushSubscription3 = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URL("https://example.com/push/?device=X8980fc&client=12c6d086")),
        deviceId = DeviceClientId("12c6d083"),
        types = Seq(MailboxTypeName, EmailDeliveryTypeName, EmailTypeName))
    val pushSubscription4 = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URL("https://example.com/push/?device=X8980fc&client=12c6d086")),
        deviceId = DeviceClientId("12c6d084"),
        types = Seq(MailboxTypeName, EmailDeliveryTypeName, EmailTypeName))

    val request: String =
      s"""{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "update": {
        |                "${pushSubscription1.id.serialise}": {
        |                  "verificationCode": "${pushSubscription1.verificationCode.value}"
        |                },
        |                "${pushSubscription2.id.serialise}": {
        |                  "verificationCode": "wrong"
        |                },
        |                "${pushSubscription3.id.serialise}": {
        |                  "verificationCode": "${pushSubscription3.verificationCode.value}"
        |                },
        |                "${pushSubscription4.id.serialise}": {
        |                  "verificationCode": "wrongAgain"
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "PushSubscription/set",
           |            {
           |                "updated": {
           |                    "${pushSubscription1.id.serialise}": {},
           |                    "${pushSubscription3.id.serialise}": {}
           |                },
           |                "notUpdated": {
           |                    "${pushSubscription2.id.serialise}": {
           |                        "type": "invalidProperties",
           |                        "description": "Wrong verification code",
           |                        "properties": [
           |                            "verificationCode"
           |                        ]
           |                    },
           |                    "${pushSubscription4.id.serialise}": {
           |                        "type": "invalidProperties",
           |                        "description": "Wrong verification code",
           |                        "properties": [
           |                            "verificationCode"
           |                        ]
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(probe.retrievePushSubscription(BOB, pushSubscription1.id).validated).isTrue
      softly.assertThat(probe.retrievePushSubscription(BOB, pushSubscription2.id).validated).isFalse
      softly.assertThat(probe.retrievePushSubscription(BOB, pushSubscription3.id).validated).isTrue
      softly.assertThat(probe.retrievePushSubscription(BOB, pushSubscription4.id).validated).isFalse
    })
  }

  @Test
  def updateShouldNotValidateVerificationCodeWhenWrong(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[PushSubscriptionProbe])
    val pushSubscription = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URL("https://example.com/push/?device=X8980fc&client=12c6d086")),
        deviceId = DeviceClientId("12c6d086"),
        types = Seq(MailboxTypeName, EmailDeliveryTypeName, EmailTypeName))

    val request: String =
      s"""{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "update": {
        |                "${pushSubscription.id.serialise}": {
        |                  "verificationCode": "wrong"
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "PushSubscription/set",
           |            {
           |                "notUpdated": {
           |                    "${pushSubscription.id.serialise}": {
           |                        "type": "invalidProperties",
           |                        "description": "Wrong verification code",
           |                        "properties": [
           |                            "verificationCode"
           |                        ]
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)

    assertThat(probe.retrievePushSubscription(BOB, pushSubscription.id).validated).isFalse
  }

  @Test
  def updateShouldFailWhenUnknownProperty(server: GuiceJamesServer): Unit = {
    val probe = server.getProbe(classOf[PushSubscriptionProbe])
    val pushSubscription = probe
      .createPushSubscription(username = BOB,
        url = PushSubscriptionServerURL(new URL("https://example.com/push/?device=X8980fc&client=12c6d086")),
        deviceId = DeviceClientId("12c6d086"),
        types = Seq(MailboxTypeName, EmailDeliveryTypeName, EmailTypeName))

    val request: String =
      s"""{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "update": {
        |                "${pushSubscription.id.serialise}": {
        |                  "unknown": "whatever"
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "PushSubscription/set",
           |            {
           |                "notUpdated": {
           |                    "${pushSubscription.id.serialise}": {
           |                        "description":"unknown property do not exist thus cannot be updated",
           |                        "properties":["unknown"],
           |                        "type":"invalidArguments"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)

    assertThat(probe.retrievePushSubscription(BOB, pushSubscription.id).validated).isFalse
  }

  @Test
  def updateShouldFailWhenInvalidId(server: GuiceJamesServer): Unit = {
    val request: String =
      s"""{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "update": {
        |                "bad": {
        |                  "verificationCode": "anyValue"
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "PushSubscription/set",
           |            {
           |                "notUpdated": {
           |                    "bad": {
           |                        "type": "invalidArguments",
           |                        "description": "Invalid UUID string: bad"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenNotFound(server: GuiceJamesServer): Unit = {
    val id = UUID.randomUUID().toString
    val request: String =
      s"""{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "update": {
        |                "$id": {
        |                  "verificationCode": "anyValue"
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    val response: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "PushSubscription/set",
           |            {
           |                "notUpdated": {
           |                    "$id": {
           |                        "type": "notFound",
           |                        "description": null
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }
}