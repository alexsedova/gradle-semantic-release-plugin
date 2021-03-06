/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.gliderpilot.gradle.semanticrelease

import com.github.zafarkhaja.semver.Version
import org.ajoberstar.gradle.git.release.base.ReleaseVersion
import org.ajoberstar.gradle.git.release.semver.NearestVersion
import org.ajoberstar.gradle.git.release.semver.SemVerStrategyState
import org.ajoberstar.grgit.Branch
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

/**
 * Created by tobias on 8/11/15.
 */
class SemanticReleaseStrategySpec extends Specification {

    SemVerStrategyState initialState = initialState('0.0.0')
    SemanticReleaseInitialStateService initialStateService = Mock() {
        initialState() >> { initialState }
    }
    SemanticReleaseNormalStrategy normalStrategy = Mock()

    @Subject
    SemanticReleaseStrategy strategy = new SemanticReleaseStrategy(initialStateService: initialStateService,
            normalStrategy: normalStrategy)

    def "selector closure is used"() {
        expect:
        !strategy.selector(null, null)

        when:
        strategy = strategy.copyWith(selector: { true })

        then:
        strategy.selector(null, null)
    }

    def "infers the normal version from the normal strategy"() {
        when:
        def releaseVersion = strategy.infer(null, null)

        then:
        1 * normalStrategy.infer(initialState) >> { initialState.copyWith(inferredNormal: '1.0.1') }

        releaseVersion == new ReleaseVersion('1.0.1', '0.0.0', false)
    }

    def "Uses initial version 1.0.0 when the normalStrategy does not change the state"() {
        when:
        def releaseVersion = strategy.infer(null, null)

        then:
        1 * normalStrategy.infer(initialState) >> { initialState }

        releaseVersion == new ReleaseVersion('1.0.0', '0.0.0', false)
    }

    def "increments the PATCH when the normalStrategy does not change the state"() {
        given:
        initialState = initialState('1.1.1')

        when:
        def releaseVersion = strategy.infer(null, null)

        then:
        1 * normalStrategy.infer(initialState) >> { initialState }

        releaseVersion == new ReleaseVersion('1.1.2', '1.1.1', false)
    }

    @Unroll
    def "inferred #inferredNormal on branch #branch with previous version #previousVersion leads to version #expectedVersion"() {
        given:
        initialState = initialState(previousVersion, branch)

        when:
        def releaseVersion = null
        try {
            releaseVersion = strategy.infer(null, null)
        } catch (e) {
            releaseVersion = e
        }

        then:
        1 * normalStrategy.infer(initialState) >> {
            initialState.nearestVersion.normal.toString() == inferredNormal ? initialState : initialState.copyWith(inferredNormal: inferredNormal)
        }
        expectedVersion ? releaseVersion == new ReleaseVersion(expectedVersion, previousVersion, false) : releaseVersion instanceof Exception

        where:
        branch          | previousVersion | inferredNormal | expectedVersion
        'release/1.x'   | '0.0.0'         | '0.0.0'        | '1.0.0'
        'release/1.x'   | '0.0.0'         | '1.0.0'        | '1.0.0'
        'release/1.x'   | '1.0.0'         | '1.0.0'        | '1.0.1'
        'release/1.x'   | '1.0.0'         | '1.0.1'        | '1.0.1'
        'release/1.x'   | '1.0.0'         | '1.1.0'        | '1.1.0'
        'release/1.x'   | '1.0.0'         | '2.0.0'        | null
        'release/1.x'   | '1.1.0'         | '1.1.0'        | '1.1.1'
        'release/1.x'   | '2.0.0'         | '2.0.0'        | null

        'release/1.0.x' | '0.0.0'         | '0.0.0'        | '1.0.0'
        'release/1.0.x' | '0.0.0'         | '1.0.0'        | '1.0.0'
        'release/1.0.x' | '1.0.0'         | '1.0.0'        | '1.0.1'
        'release/1.0.x' | '1.0.0'         | '1.0.1'        | '1.0.1'
        'release/1.0.x' | '1.0.0'         | '1.1.0'        | null
        'release/1.0.x' | '1.0.0'         | '2.0.0'        | null
        'release/1.0.x' | '1.1.0'         | '1.1.0'        | null
        'release/1.0.x' | '2.0.0'         | '2.0.0'        | null
    }

    def initialState(String version, String branch = 'master') {
        new SemVerStrategyState(
                nearestVersion: new NearestVersion(normal: Version.valueOf(version)), currentBranch: new Branch(fullName: branch)
        )
    }
}