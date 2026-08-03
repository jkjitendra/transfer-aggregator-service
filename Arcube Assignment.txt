# Arcube Sr. Java Backend Engineer Final Round Task: Airport Transfer

# Aggregator Microservice

Most airlines only sell seats and bags in addition to flights, missing out on significant revenue from
high-demand, innovative add-ons. Arcube helps airlines launch 16+ ancillary products such as
eSIMs, insurance, and car rentals, through a single integration in a matter of weeks, eliminating
years of complex and costly supplier sourcing, negotiation, and integrations.

Watch this 5 - minute video to understand Arcube’s value proposition -
https://www.loom.com/share/9a01c81afd2a408ebf412cdfe5408faf

We are a leading third-party ancillary aggregator and that requires delivering a platform that is
scalable, easy to extend, and straightforward to maintain. Arcube currently offers a wide range of
ancillary product types through its platform, including eSIMs, car hire, tours and activities, and
hotels. One important product category that is currently missing and which Arcube intends to add is
**airport transfers**. Introducing airport transfers will allow passengers to book ground transportation
between their home or hotel and the airport, in both directions.

# Communication & Coordination

We will create a **WhatsApp group chat** that includes Arcube personnel and all the candidates. If
you have any questions during the assessment, please post them in this group and so that responses
are visible to all participants.

We will hold **guided check-in meetings on Saturday and Sunday mornings at 0 7 :00 am (UK
time)**.

- **Saturday session**
    This session will focus on providing initial guidance and addressing any early questions. As
    part of the meeting, we will also walk through Arcube’s product so that candidates can gain
    a clear understanding of the end-user experience.
- **Sunday session**
    This session will focus on reviewing progress, discussing implementation challenges, and
    addressing any outstanding issues or questions.

These touchpoints are intended to support candidates while ensuring shared context and consistent
guidance throughout the assessment.

Arcube team members will be available to answer questions via WhatsApp on both days and can
arrange ad-hoc 1-to-1 calls if further clarification is needed at the start of, or during, the task.


# Existing Architecture

Arcube’s current architecture consists of the following microservices:

- **Aggregator**
    The central microservice responsible for routing requests to the appropriate product-type
    aggregators. This service is responsible for partially analyzing incoming requests and, using
    a **scatter–gather** approach, communicating with the relevant product-type aggregators (such
    as the eSIM and/or car hire aggregators) to retrieve search results, perform availability
    checks, and execute bookings.
- **eSIM Aggregator**
    Receives requests, analyses them, and contacts the appropriate vendors based on tenant
    configuration, request origin country, and supplier availability.
- **Car Hire Aggregator**
    Performs similar responsibilities for car hire suppliers.
- **OFMS/ORMS**
    Our central offer & order management system which is responsible for handling order
    management, states etc. Everything that needs to be persisted as part of an order or a search
    query result its persisted here.

# Objective

The task is to design and implement a **new Java-based aggregator microservice** responsible for
managing airport transfer suppliers.

To support this, you will be provided with the documentation of one airport transfer supplier,
**Mozio**. You are expected to study this documentation and, together with the end-to-end user journey
that we will share with you on our first call, design and implement the microservice.

The goal is to deliver a microservice that is as complete as possible, meaning it should support -
mock the core functionalities offered by Mozio (search, book, cancel) and expose the appropriate
APIs. So that Arcube’s central Aggregator service can consume them. As we have not shared the
API of the central aggregator, feel free to create the required endpoints on your microservice which
the aggregator should call to perform search, book and cancel operations.

# Key Focus Areas

When implementing the microservice:

- Do **not** focus primarily on the low-level details of the Mozio integration itself (implementing the
    Mozio calls themselves; mocking the Mozio responses is enough).
- Instead, prioritize:
    - A clean and extensible architecture that allows additional airport transfer suppliers to be
       added easily.
    - Clear separation of concerns between vendor-specific logic and shared domain logic.


- Proper logging and observability, including logs suitable for distributed tracing and
    production support.
- Design considerations expected from a SaaS platform serving end customers (e.g.
    maintainability, debuggability, and operational readiness).
- The microservice should be scalable both horizontally and vertically.

# Scope and Expectations

This exercise is intentionally open-ended. Its purpose is to assess your understanding of what is
required to build software that forms part of a successful, production-grade product.

You should approach this task as the **team lead** : your responsibility is to deliver a solid foundation
and clear structure that can be handed over to junior developers, who should be able to continue and
extend your work with confidence.

Remember that what you are building is not an offer or order management system. You should
focus on the aggregation itself and things that are important on an aggregation engine. While
building the microservice, you will see that to create a booking in Mozio, you will need the search
result for which you want to create the booking, assume that this will be given to your microservice
from the layer above.

# Handover Requirement

At the end of the exercise, you must provide a **Git repository** containing the implemented
microservice, along with **clear, step-by-step instructions** on how to build and run the service
locally.

The repository must include, at minimum:

- A complete codebase for the airport transfer aggregator microservice
- A README.md with:
    - Prerequisites (e.g., Java version, build tool, required environment variables)
    - How to build (e.g., Maven/Gradle commands)
    - How to run (local run instructions and/or Docker-based run instructions)
    - How to execute tests (if applicable)
    - Example configuration (sample .env, application.yml, or similar)
    - Example API calls (e.g., curl examples or a Postman collection link/file, if
       included)
- Any supporting artifacts required to run the service (e.g., Dockerfile, docker-compose, local
    configuration templates), as applicable

This handover should enable a reviewer to clone the repository and run the service with minimal
effort and enable junior developers to continue development confidently.


We acknowledge that this is a demanding task and that completing every element within two days is
unlikely. The task is intentionally open-ended so we can assess how you prioritise, structure your
solution, and reason for trade-offs.


